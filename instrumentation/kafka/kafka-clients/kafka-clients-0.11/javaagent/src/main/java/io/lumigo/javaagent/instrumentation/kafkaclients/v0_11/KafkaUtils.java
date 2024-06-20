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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.common.header.Headers;

public class KafkaUtils {

  public static List<String> convertHeadersToString(Headers headers) {
    return Arrays.stream(headers.toArray())
        .map(header -> header.key() + "=" + Arrays.toString(header.value()))
        .collect(Collectors.toList());
  }
}
