package io.lumigo.javaagent.instrumentation.storm;

import org.apache.storm.tuple.AddressedTuple;
import org.apache.storm.tuple.Tuple;
import java.util.List;
import java.util.stream.Collectors;

public class StormUtils {

  public static String getThreadName() {
    return Thread.currentThread().getName();
  }

  public static String getServiceName() {
    // The only way to get the name is from the thread name
    return getThreadName().split("-")[2];
  }

  public static String getStormVersion(Tuple tuple) {
    return tuple.getContext().getRawTopology().get_storm_version();
  }

  public static String getStormId(Tuple tuple) {
    return tuple.getContext().getStormId();
  }

  public static String getSourceComponent(Tuple tuple) {
    return tuple.getSourceComponent();
  }

  public static List<String> getValues(Tuple tuple) {
    return tuple.getValues().stream().map(Object::toString).collect(Collectors.toList());
  }

  public static String getMessageId(Tuple tuple) {
    return tuple.getMessageId().toString();
  }

  public static String getDestComponent(AddressedTuple addressedTuple) {
    return addressedTuple.tuple.getContext().getComponentId(addressedTuple.getDest());
  }

}
