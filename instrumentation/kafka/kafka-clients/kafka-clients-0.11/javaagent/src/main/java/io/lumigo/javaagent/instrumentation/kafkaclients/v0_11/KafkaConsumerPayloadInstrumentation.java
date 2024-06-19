package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProcessRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.util.Collections;

public class KafkaConsumerPayloadInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("createConsumerOperationInstrumenter")
            .and(isPublic()),
        this.getClass().getName() + "$CreateConsumerOperationInstrumenterAdvice");
  }

  public static class CreateConsumerOperationInstrumenterAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) MessageOperation operation,
        @Advice.Argument(0) Iterable<AttributesExtractor<KafkaProcessRequest, Void>> extractors
    ) {
      System.out.println("KafkaConsumerPayloadInstrumentation.CreateConsumerOperationInstrumenterAdvice.onEnter");
      if (operation == MessageOperation.PROCESS && !extractors.iterator().hasNext()) {
        System.out.println("Added ConsumerPayloadAttributeExtractor to extractors");
        extractors = Collections.singletonList(new ConsumerPayloadAttributeExtractor());
      }
    }
  }
}
