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
import java.util.Random;
import java.util.UUID;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

public class RandomSentenceSpout extends BaseRichSpout {
  private static int counter = 0;
  private SpoutOutputCollector collector;
  private Random rand;

  @Override
  public void open(
      Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
    this.collector = collector;
    rand = new Random();
  }

  @Override
  public void nextTuple() {
    if (counter > 0) {
      // We want to exit after 1 tuple because we want only one trace in the instrumentation test
      Utils.sleep(100000);
      return;
    }
    String[] sentences =
        new String[] {
          sentence("the cow jumped over the moon"),
          sentence("an apple a day keeps the doctor away"),
          sentence("four score and seven years ago"),
          sentence("snow white and the seven dwarfs"),
          sentence("i am at two with nature")
        };
    final String sentence = sentences[rand.nextInt(sentences.length)];

    String msgID = UUID.randomUUID().toString();
    System.out.println("Spout.execute: " + sentence + " MessageId: " + msgID);
    collector.emit(new Values(sentence), msgID);
    counter += 1;
  }

  private String sentence(String input) {
    return input;
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
