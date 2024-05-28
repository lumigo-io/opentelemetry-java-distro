package io.lumigo.javaagent.instrumentation.storm;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.lumigo.javaagent.instrumentation.storm.StormSingleton.stormInstrumenter;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class StormInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.storm.topology.base.BaseRichBolt"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
//    typeTransformer.applyAdviceToMethod(
//        named("execute"),
//        StormInstrumentation.class.getName() + "$StormExecuteAdvice");
  }

  public static class StormExecuteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Object request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope
    ) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!stormInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = stormInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }
  }

  @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
  public static void onExit(@Advice.Argument(1) Object request,
      @Advice.Return Object response,
      @Advice.Thrown Throwable exception,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();
    stormInstrumenter().end(context, request, response, exception);
  }
}
