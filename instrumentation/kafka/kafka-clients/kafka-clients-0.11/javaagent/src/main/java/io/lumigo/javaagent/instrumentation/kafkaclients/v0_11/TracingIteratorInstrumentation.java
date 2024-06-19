package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.lumigo.instrumentation.core.LumigoSemanticAttributes;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;


public class TracingIteratorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.TracingIterator");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("next")
            .and(isPublic()),
        this.getClass().getName() + "$NextAdvice");
  }

  public static class NextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnd(
        @Advice.Return ConsumerRecord<?, ?> next
    ) {
      System.out.println("TracingIteratorInstrumentation.NextAdvice.onEnd");
      if (null != next.value()) {
        Java8BytecodeBridge.currentSpan()
            .setAttribute(LumigoSemanticAttributes.MESSAGING_PAYLOAD, next.value().toString());
      }
    }
  }
}
