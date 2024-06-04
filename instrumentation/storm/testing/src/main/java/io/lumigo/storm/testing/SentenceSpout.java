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

import java.util.Map;
import java.util.UUID;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

public class SentenceSpout extends BaseRichSpout {
  private static final long serialVersionUID = 1L;
  private int counter = 0;
  private int numberOfTuples = 1;
  private transient SpoutOutputCollector collector;

  public SentenceSpout(int numberOfTuples) {
    super();
    this.numberOfTuples = numberOfTuples;
  }

  @Override
  public void open(
      Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
    this.collector = collector;
  }

  @Override
  public void nextTuple() {
    if (counter >= numberOfTuples) {
      // We want to stop generating tuples after we have emitted the required number of tuples
      Utils.sleep(1000);
      counter += 1;
      return;
    }
    final String sentence = "the cow jumped over the moon";

    String msgID = UUID.randomUUID().toString();
    collector.emit(new Values(sentence), msgID);
    counter += 1;
  }

  @Override
  public void ack(Object id) {}

  @Override
  public void fail(Object id) {}

  @Override
  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(new Fields("word"));
  }
}
