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
package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class PayloadTracingIterable<K, V> implements Iterable<ConsumerRecord<K, V>> {
  private final Iterable<ConsumerRecord<K, V>> delegate;
  private boolean firstIterator = true;

  protected PayloadTracingIterable(Iterable<ConsumerRecord<K, V>> delegate) {
    this.delegate = delegate;
  }

  public static <K, V> Iterable<ConsumerRecord<K, V>> wrap(
      Iterable<ConsumerRecord<K, V>> delegate) {
    if (KafkaClientsConsumerProcessTracing.wrappingEnabled()) {
      return new PayloadTracingIterable<>(delegate);
    }
    return delegate;
  }

  @Override
  public Iterator<ConsumerRecord<K, V>> iterator() {
    Iterator<ConsumerRecord<K, V>> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // ConsumerRecords is performed in the same thread that called poll()
    if (firstIterator) {
      it = PayloadTracingIterator.wrap(delegate.iterator());
      firstIterator = false;
    } else {
      it = delegate.iterator();
    }

    return it;
  }
}
