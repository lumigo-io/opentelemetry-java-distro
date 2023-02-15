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
package io.lumigo.javaagent;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@AutoService(ResourceProvider.class)
public class LumigoDistroResource implements ResourceProvider {
  private static final String LUMIGO_DISTRO_VERSION = "lumigo.distro.version";
  private static final String DISTRO_VERSION = getVersion();

  private static String getVersion() {
    try (InputStream in =
        ResourceProvider.class.getClassLoader().getResourceAsStream("lumigo.properties")) {
      if (in == null) {
        return "dev";
      }
      Properties splunkProps = new Properties();
      splunkProps.load(in);
      AttributeKey<String> key = AttributeKey.stringKey(LUMIGO_DISTRO_VERSION);
      return splunkProps.getProperty(key.getKey());
    } catch (IOException e) {
      return "dev";
    }
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    AttributesBuilder ab = Attributes.builder();

    ab.put(LUMIGO_DISTRO_VERSION, DISTRO_VERSION);
    return Resource.create(ab.build());
  }
}
