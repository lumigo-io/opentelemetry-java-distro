package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import org.apache.kafka.common.header.Headers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class KafkaUtils {

  public static List<String> convertHeadersToString(Headers headers) {
    return Arrays.stream(headers.toArray()).map(header -> header.key() + "=" + Arrays.toString(header.value())).collect(
        Collectors.toList());
  }
}
