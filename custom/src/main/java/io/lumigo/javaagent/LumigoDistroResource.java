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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoService(ResourceProvider.class)
public class LumigoDistroResource implements ResourceProvider {
  private static final String LUMIGO_DISTRO_VERSION = "lumigo.distro.version";
  private static final String DISTRO_VERSION = getVersion();

  private static String getVersion() {
    String version = LumigoDistroResource.class.getPackage().getImplementationVersion();
    if (version != null) {
      try {
        Matcher pt = Pattern.compile("^lumigo\\-(.*)?\\-otel.*$").matcher(version);
        if (pt.find()) {
          return pt.group(1);
        }
      } catch (Exception e) {
      }
    }
    return "dev";
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    AttributesBuilder ab = Attributes.builder();

    ab.put(LUMIGO_DISTRO_VERSION, DISTRO_VERSION);
    return Resource.create(ab.build());
  }
}
