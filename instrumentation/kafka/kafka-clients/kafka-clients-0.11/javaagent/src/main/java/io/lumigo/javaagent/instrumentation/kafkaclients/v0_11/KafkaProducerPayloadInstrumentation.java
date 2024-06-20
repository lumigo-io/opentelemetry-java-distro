package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import io.lumigo.instrumentation.core.LumigoSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.producer.ProducerRecord;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class KafkaProducerPayloadInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.producer.KafkaProducer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("send"))
            .and(takesArgument(0, named("org.apache.kafka.clients.producer.ProducerRecord")))
            .and(takesArgument(1, named("org.apache.kafka.clients.producer.Callback"))),
        KafkaProducerPayloadInstrumentation.class.getName() + "$SendPayloadAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendPayloadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) ProducerRecord<?, ?> record
    ) {
      if (null != record.value()) {
        Span span = Java8BytecodeBridge.currentSpan();
        span.setAttribute(LumigoSemanticAttributes.MESSAGING_PAYLOAD, record.value().toString());
        span.setAttribute(LumigoSemanticAttributes.MESSAGING_HEADERS, KafkaUtils.convertHeadersToString(record.headers()));
      }
    }
  }
}
