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

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Iterator;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;

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
