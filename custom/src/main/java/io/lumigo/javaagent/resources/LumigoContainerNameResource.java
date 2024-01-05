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

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;

@AutoService(ResourceProvider.class)
public class LumigoContainerNameResource implements ResourceProvider {
  private static final String LUMIGO_CONTAINER_NAME_ENV_VAR = "lumigo.container.name";

  @Override
  public Resource createResource(ConfigProperties config) {
    String containerName = System.getenv(LUMIGO_CONTAINER_NAME_ENV_VAR);
    if (containerName != null && !containerName.isEmpty()) {
      AttributesBuilder ab = Attributes.builder();
      ab.put(ResourceAttributes.K8S_CONTAINER_NAME, containerName);
      return Resource.create(ab.build());
    }

    return Resource.empty();
  }
}
