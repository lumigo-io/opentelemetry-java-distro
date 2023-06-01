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
package io.lumigo.javaagent.resources;

import io.lumigo.javaagent.common.ProcessEnvironmentScrubber;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;

public final class ProcessEnvironmentResource {
  public static final String PROCESS_ENVIRONMENT = "process.environ";

  private static Resource INSTANCE;

  public static Resource get(ConfigProperties config) {
    if (null == INSTANCE) {
      INSTANCE = new ProcessEnvironmentResource().buildResource(config);
    }
    return INSTANCE;
  }

  private Resource buildResource(ConfigProperties config) {
    AttributesBuilder ab = Attributes.builder();
    ab.put(PROCESS_ENVIRONMENT, new ProcessEnvironmentScrubber(config).scrub(System.getenv()));

    return Resource.create(ab.build());
  }
}
