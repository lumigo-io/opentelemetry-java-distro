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

import org.apache.storm.topology.ConfigurableTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

/** This topology demonstrates Storm's stream groupings and multilang capabilities. */
public class WordCountTopology extends ConfigurableTopology {
  public static void main(String[] args) {
    ConfigurableTopology.start(new WordCountTopology(), args);
  }

  @Override
  protected int run(String[] args) {

    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new RandomSentenceSpout(), 2);
    builder.setBolt("split", new SplitBolt(), 4).shuffleGrouping("spout");
    builder
        .setBolt("wordCount", new WordCountBolt(), 6)
        .fieldsGrouping("split", new Fields("word"));

    conf.setDebug(true);
    conf.setStatsSampleRate(1.0D);
    conf.setNumWorkers(1);
    conf.setNumAckers(1);

    String topologyName = "word-count";
    if (args != null && args.length > 0) {
      topologyName = args[0];
    }
    return submit(topologyName, conf, builder);
  }
}
