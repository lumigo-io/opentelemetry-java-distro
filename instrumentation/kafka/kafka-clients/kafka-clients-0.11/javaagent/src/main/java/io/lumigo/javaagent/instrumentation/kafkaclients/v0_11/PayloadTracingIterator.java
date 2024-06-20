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

import io.lumigo.instrumentation.core.LumigoSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.semconv.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public class PayloadTracingIterator<K, V> implements Iterator<ConsumerRecord<K, V>> {

  private static final String LUMIGO_MESSAGE_ID_KEY = "lumigoMessageId";

  private final Iterator<ConsumerRecord<K, V>> delegateIterator;

  private PayloadTracingIterator(Iterator<ConsumerRecord<K, V>> delegateIterator) {
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
    ConsumerRecord<K, V> next = delegateIterator.next();
    if (next != null && KafkaClientsConsumerProcessTracing.wrappingEnabled()) {
      Span span = Java8BytecodeBridge.currentSpan();
      span.setAttribute(LumigoSemanticAttributes.MESSAGING_PAYLOAD, next.value().toString());
      span.setAttribute(
          LumigoSemanticAttributes.MESSAGING_HEADERS,
          KafkaUtils.convertHeadersToString(next.headers()));

      // Support for lumigo lambda tracer kafka instrumentation
      Header messageIdHeader = next.headers().lastHeader(LUMIGO_MESSAGE_ID_KEY);
      if (messageIdHeader != null) {
        span.setAttribute(
            SemanticAttributes.MESSAGING_MESSAGE_ID,
            new String(messageIdHeader.value(), StandardCharsets.UTF_8));
      }
    }
    return next;
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }
}
