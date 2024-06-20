/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerContextUtil;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Iterator;
import java.util.List;
import io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.ConsumerRecordsInstrumentation;
import io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.TracingList;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class ConsumerRecordsPayloadInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.ConsumerRecords");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("records"))
            .and(takesArgument(0, String.class))
            .and(returns(Iterable.class)),
        ConsumerRecordsPayloadInstrumentation.class.getName() + "$IterableAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("records"))
            .and(takesArgument(0, named("org.apache.kafka.common.TopicPartition")))
            .and(returns(List.class)),
        ConsumerRecordsPayloadInstrumentation.class.getName() + "$ListAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("iterator"))
            .and(takesArguments(0))
            .and(returns(Iterator.class)),
        ConsumerRecordsPayloadInstrumentation.class.getName() + "$IteratorAdvice");
  }

  @SuppressWarnings("unused")
  public static class IterableAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void wrap(
        @Advice.Return(readOnly = false) Iterable<ConsumerRecord<K, V>> iterable) {
      iterable = PayloadTracingIterable.wrap(iterable);
    }
  }

  @SuppressWarnings("unused")
  public static class ListAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void wrap(
        @Advice.Return(readOnly = false) List<ConsumerRecord<K, V>> list) {
      list = PayloadTracingList.wrap(list);
    }
  }


  @SuppressWarnings("unused")
  public static class IteratorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void wrap(
        @Advice.Return(readOnly = false) Iterator<ConsumerRecord<K, V>> iterator) {
      iterator = PayloadTracingIterator.wrap(iterator);
    }
  }
}
