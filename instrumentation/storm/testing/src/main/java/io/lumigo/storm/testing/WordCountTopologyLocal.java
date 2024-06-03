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

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

@Slf4j
public class WordCountTopologyLocal {

  private static LocalCluster cluster;

  public static void submitTopology() throws Exception {
    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new RandomSentenceSpout(), 1);
    builder.setBolt("split", new SplitBolt(), 1).shuffleGrouping("spout");
    builder
        .setBolt("wordCount", new WordCountBolt(), 1)
        .fieldsGrouping("split", new Fields("word"));

    Config conf = new Config();
    conf.setDebug(true);
    conf.setStatsSampleRate(1.0D);
    conf.setNumWorkers(1);
    conf.setNumAckers(1);

    String topologyName = "word-count";

    cluster = new LocalCluster();
    cluster.submitTopology(topologyName, conf, builder.createTopology());
  }

  public static void close() throws Exception {
    cluster.close();
  }
}
