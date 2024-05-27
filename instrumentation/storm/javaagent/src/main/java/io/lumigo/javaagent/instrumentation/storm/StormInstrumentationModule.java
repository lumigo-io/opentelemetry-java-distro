package io.lumigo.javaagent.instrumentation.storm;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class StormInstrumentationModule extends InstrumentationModule {

  protected StormInstrumentationModule() {
    super("storm", "lumigo-storm");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return null;
  }
}
