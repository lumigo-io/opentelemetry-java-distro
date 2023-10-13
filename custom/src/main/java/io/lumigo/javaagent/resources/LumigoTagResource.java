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
import java.util.logging.Logger;

@AutoService(ResourceProvider.class)
public class LumigoTagResource implements ResourceProvider {
  private static final Logger LOGGER = Logger.getLogger(LumigoTagResource.class.getName());

  private static final String LUMIGO_TAG_ENV_VAR = "lumigo.tag";

  @Override
  public Resource createResource(ConfigProperties config) {
    String lumigoTag = System.getenv(LUMIGO_TAG_ENV_VAR);
    if (lumigoTag != null && !lumigoTag.isEmpty()) {
      if (lumigoTag.contains(";")) {
        LOGGER.warning(
            "Lumigo tag cannot contain the ';' character. The tag specified with "
                + LUMIGO_TAG_ENV_VAR
                + " will be ignored.");
        return Resource.empty();
      }

      AttributesBuilder ab = Attributes.builder();
      ab.put(LUMIGO_TAG_ENV_VAR, lumigoTag);
      return Resource.create(ab.build());
    }

    return Resource.empty();
  }
}
