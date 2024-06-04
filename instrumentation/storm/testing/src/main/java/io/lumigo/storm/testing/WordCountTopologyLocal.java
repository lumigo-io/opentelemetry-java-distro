/*
 * Copyright 2024 Lumigo LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.lumigo.storm.testing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.Config;
import org.apache.storm.ILocalCluster;
import org.apache.storm.Testing;
import org.apache.storm.testing.CompleteTopologyParam;
import org.apache.storm.testing.FixedTuple;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.junit.jupiter.api.Assertions;

@Slf4j
public class WordCountTopologyLocal {
  public static void runLocalClusterTest(ILocalCluster cluster) throws Exception {
    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new SentenceSpout(1), 1);
    builder.setBolt("split", new SplitBolt(), 1).shuffleGrouping("spout");
    builder
        .setBolt("wordCount", new WordCountBolt(), 1)
        .fieldsGrouping("split", new Fields("word"));

    // prepare the mock data
    MockedSources mockedSources = new MockedSources();
    mockedSources.addMockData("spout", new Values("the cow jumped over the moon"));

    Config topoConf = new Config();
    topoConf.setNumWorkers(1);
    CompleteTopologyParam ctp = new CompleteTopologyParam();
    ctp.setStormConf(topoConf);
    ctp.setMockedSources(mockedSources);

    Map<String, List<FixedTuple>> results =
        Testing.completeTopology(cluster, builder.createTopology(), ctp);

    // Verify the topology data
    List<List<Object>> spoutTuples = Testing.readTuples(results, "spout");
    List<List<Object>> expectedSpoutTuples =
        Collections.singletonList(
            Collections.singletonList("the cow jumped over the moon"));

    Assertions.assertTrue(
        Testing.multiseteq(expectedSpoutTuples, spoutTuples),
        expectedSpoutTuples + " expected, but found " + spoutTuples);

    List<List<Object>> splitTuples = Testing.readTuples(results, "split");
    List<List<Object>> expectedSplitTuples =
            Arrays.asList(
                Collections.singletonList("the"),
                Collections.singletonList("cow"),
                Collections.singletonList("jumped"),
                Collections.singletonList("over"),
                Collections.singletonList("the"),
                Collections.singletonList("moon"));
    Assertions.assertTrue(
        Testing.multiseteq(expectedSplitTuples, splitTuples),
        expectedSplitTuples + " expected, but found " + splitTuples);

    List<List<Object>> threeTuples = Testing.readTuples(results, "wordCount");
    List<List<Object>> expectedThreeTuples =
        Arrays.asList(
            Arrays.asList("the", 1),
            Arrays.asList("cow", 1),
            Arrays.asList("jumped", 1),
            Arrays.asList("over", 1),
            Arrays.asList("the", 2),
            Arrays.asList("moon", 1));
    Assertions.assertTrue(
        Testing.multiseteq(expectedThreeTuples, threeTuples),
        expectedThreeTuples + " expected, but found " + threeTuples);
  }
}
