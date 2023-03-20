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
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.BufferedReader;
import java.io.FileReader;

@AutoService(ResourceProvider.class)
public class KubernetesResource implements ResourceProvider {
  private static final Boolean IS_KUBERNETES = isContainerOnKubernetes();

  private static final String KUBERNETES_MANAGED_HOSTS_FILE = "# Kubernetes-managed hosts file";

  private static boolean isContainerOnKubernetes() {
    try {
      BufferedReader br = new BufferedReader(new FileReader("/etc/hosts"));
      Boolean ret = br.readLine().startsWith(KUBERNETES_MANAGED_HOSTS_FILE);
      br.close();
      return ret;
    } catch (Exception e) {
      return false;
    }
  }

  private static final int POD_ID_LENGTH = 36;
  private static final String KUBERNETES_POD_UID = GetKubernetesPodUid();

  private static String GetKubernetesPodUid() {
    String podId = GetKubernetesPodUidV1();
    if (podId == null) {
      podId = GetKubernetesPodUidV2();
    }
    return podId;
  }

  private static String GetKubernetesPodUidV1() {
    String podId = null;
    try {
      BufferedReader br = new BufferedReader(new FileReader("/proc/self/mountinfo"));
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.length() > POD_ID_LENGTH && line.contains("/pods/")) {
          podId = line.split("/pods/")[1].substring(0, POD_ID_LENGTH);
          break;
        }
      }
      br.close();
    } catch (Exception e) {
    }

    return podId;
  }

  private static String GetKubernetesPodUidV2() {
    String podId = null;
    try {
      BufferedReader br = new BufferedReader(new FileReader("/proc/self/cgroup"));
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.length() > POD_ID_LENGTH) {
          String[] lineInfo = line.split("/");
          if (lineInfo.length > 2
              && lineInfo[lineInfo.length - 2].startsWith("pod")
              && lineInfo[lineInfo.length - 2].length() == POD_ID_LENGTH + 3) {
            podId = lineInfo[lineInfo.length - 2].substring(3, 3 + POD_ID_LENGTH);
          } else {
            podId = lineInfo[lineInfo.length - 2];
          }
        }
      }
      br.close();
    } catch (Exception e) {
    }

    return podId;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    if (!IS_KUBERNETES || KUBERNETES_POD_UID.isEmpty()) {
      return null;
    }

    AttributesBuilder ab = Attributes.builder();
    ab.put(ResourceAttributes.K8S_POD_UID, KUBERNETES_POD_UID);
    return Resource.create(ab.build());
  }
}
