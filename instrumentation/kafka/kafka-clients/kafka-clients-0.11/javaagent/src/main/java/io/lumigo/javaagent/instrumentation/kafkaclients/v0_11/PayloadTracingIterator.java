/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import io.lumigo.instrumentation.core.LumigoSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class PayloadTracingIterator<K, V> implements Iterator<ConsumerRecord<K, V>> {

  private final Iterator<ConsumerRecord<K, V>> delegateIterator;

  private PayloadTracingIterator(
      Iterator<ConsumerRecord<K, V>> delegateIterator) {
    this.delegateIterator = delegateIterator;
  }

  public static <K, V> Iterator<ConsumerRecord<K, V>> wrap(
      Iterator<ConsumerRecord<K, V>> delegateIterator) {
    if (KafkaClientsConsumerProcessTracing.wrappingEnabled()) {
      return new PayloadTracingIterator<>(delegateIterator);
    }
    return delegateIterator;
  }

  @Override
  public boolean hasNext() {
    return delegateIterator.hasNext();
  }

  @Override
  public ConsumerRecord<K, V> next() {
    System.out.println("PayloadTracingIterator.next");
    ConsumerRecord<K, V> next = delegateIterator.next();
    if (next != null && KafkaClientsConsumerProcessTracing.wrappingEnabled()) {
      System.out.println("PayloadTracingIterator.next not null");
      System.out.println(next.getClass());
      System.out.println(delegateIterator.getClass());
      Context context = Java8BytecodeBridge.currentContext();
      Span span = Java8BytecodeBridge.spanFromContext(context);
      System.out.println(span.getSpanContext().getSpanId());
      span.setAttribute(LumigoSemanticAttributes.MESSAGING_PAYLOAD, next.value().toString());
      Java8BytecodeBridge.currentSpan()
            .setAttribute(LumigoSemanticAttributes.MESSAGING_PAYLOAD, next.value().toString());
    }
    return next;
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }
}
