/*
 * Copyright 2023 Lumigo LTD
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

import io.lumigo.instrumentation.core.LumigoSemanticAttributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProcessRequest;
import javax.annotation.Nullable;

public class ConsumerPayloadAttributeExtractor implements AttributesExtractor<KafkaProcessRequest, Void> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      KafkaProcessRequest kafkaProcessRequest) {
    if (null != kafkaProcessRequest.getRecord().value()) {
      attributes.put(
          LumigoSemanticAttributes.MESSAGING_PAYLOAD,
          kafkaProcessRequest.getRecord().value().toString());
    }
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      KafkaProcessRequest kafkaProcessRequest, @Nullable Void unused, @Nullable Throwable error) {
    // Do nothing
  }
}
