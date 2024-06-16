package io.lumigo.javaagent.instrumentation.lettuce.v5_1;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.List;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class LettucePayloadInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing$OpenTelemetrySpan");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        named("finish")
        .and(isPrivate())
        .and(takesArguments(1)),
            LettucePayloadInstrumentation.class.getName() + "$LettucePayloadAdvice");
  }

  @SuppressWarnings("unused")
  public static class LettucePayloadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Span span,
        @Advice.FieldValue(value = "name") String name,
        @Advice.FieldValue(value = "argsList") List<String> argsList,
        @Advice.Local(value = "argsString") String argsString
    ) {
      System.out.println("LettucePayloadAdvice.onEnter");
      if (name != null) {
        String statementPayload = argsString != null ? argsString : String.join(" ", argsList);
        span.setAttribute("db.statement.payload", statementPayload);
      }
    }
  }
}
