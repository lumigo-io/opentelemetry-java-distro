/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerContext;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.TracingIterable;
import io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.TracingIterator;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class PayloadTracingIterable<K, V> implements Iterable<ConsumerRecord<K, V>> {
  private final Iterable<ConsumerRecord<K, V>> delegate;
  private boolean firstIterator = true;

  protected PayloadTracingIterable(
      Iterable<ConsumerRecord<K, V>> delegate) {
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
