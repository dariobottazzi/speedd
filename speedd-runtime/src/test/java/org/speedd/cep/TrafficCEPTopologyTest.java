package org.speedd.cep;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fest.util.Files;
import org.junit.Ignore;
import org.junit.Test;
import org.speedd.data.Event;
import org.speedd.kafka.JsonEventDecoder;
import org.speedd.test.FileReaderSpout;
import org.speedd.test.FileWriterBolt;
import org.speedd.traffic.TrafficAggregatedReadingScheme;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.Scheme;
import backtype.storm.topology.TopologyBuilder;

import com.ibm.hrl.proton.ProtonTopologyBuilder;

@SuppressWarnings("serial")
public class TrafficCEPTopologyTest implements Serializable {

	// FIXME carefully choose grouping strategy
	public StormTopology buildTopology(FileWriterBolt eventConsumer) {
		TopologyBuilder builder = new TopologyBuilder();

		File inEventsFile = new File(getClass().getClassLoader().getResource("reading1.csv").getPath());
		
		Scheme eventScheme = new TrafficAggregatedReadingScheme();
		
		FileReaderSpout fileReaderSpout = new FileReaderSpout(inEventsFile, eventScheme);

		ProtonTopologyBuilder protonTopologyBuilder = new ProtonTopologyBuilder();

		String epnPath = this.getClass().getClassLoader()
				.getResource("cnrsUnitTest.json").getPath();

		protonTopologyBuilder.buildProtonTopology(builder, fileReaderSpout,
				eventConsumer, "event-consumer", epnPath);

		return builder.createTopology();
	}

	@Test
	public void testCepTopology() throws Exception {
		Config conf = new Config();

		conf.setDebug(true);

		conf.setMaxTaskParallelism(1);

		File outFile = Files.newTemporaryFile();

		System.out.println("Output file: " + outFile.getAbsolutePath());

		FileWriterBolt eventConsumer = new FileWriterBolt(outFile);

		StormTopology cep = buildTopology(eventConsumer);

		LocalCluster cluster = new LocalCluster();

		cluster.submitTopology("cep-test", conf, cep);

		Thread.sleep(10000);

		JsonEventDecoder jsonEventDecoder = new JsonEventDecoder();

		BufferedReader reader = new BufferedReader(new FileReader(outFile));

		List<Event> receivedEvents = new ArrayList<Event>();

		while (true) {
			String eventJson = reader.readLine();

			if (eventJson == null) {
				reader.close();
				break;
			}

			Event event = jsonEventDecoder.fromBytes(eventJson
					.getBytes("UTF-8"));
			receivedEvents.add(event);
		}

		assertEquals(1, receivedEvents.size());

		Event event = receivedEvents.get(0);

		assertEquals("PredictedCongestion", event.getEventName());

		Map<String, Object> attributes = event.getAttributes();
		
		assertNotNull("'location' attribute missing",(String)attributes.get("location"));
		assertNotNull("'average_density' attribute missing", (Number)attributes.get("average_density"));
		assertNotNull("'problem_id' attribute missing", (String)attributes.get("problem_id"));
		assertNotNull("'Certainty' attribute missing", (Number)attributes.get("Certainty"));

		System.out.println(event.toString());

		cluster.shutdown();

		outFile.delete();
	}

}
