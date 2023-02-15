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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@AutoService(ResourceProvider.class)
public class ECSResource implements ResourceProvider {

  private static final String ECS_CONTAINER_METADATA_URI_V4 =
      System.getenv("ECS_CONTAINER_METADATA_URI_V4");

  private static final Resource ecs_resource = getECSResource();

  private static Resource getECSResource() {
    if (ECS_CONTAINER_METADATA_URI_V4 == null) {
      return null;
    }

    OkHttpClient client =
        new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    AttributesBuilder ab = Attributes.builder();

    try {
      Response container_metadata =
          client
              .newCall(new Request.Builder().url(ECS_CONTAINER_METADATA_URI_V4).build())
              .execute();
      if (container_metadata.isSuccessful()) {
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<HashMap<String, String>> typeRef =
            new TypeReference<HashMap<String, String>>() {};
        Map<String, String> values = mapper.readValue(container_metadata.body().string(), typeRef);

        ab.put(ResourceAttributes.AWS_ECS_CONTAINER_ARN, values.get("ContainerARN"));
      }

      Response task_metadata =
          client
              .newCall(new Request.Builder().url(ECS_CONTAINER_METADATA_URI_V4 + "/task").build())
              .execute();
      if (task_metadata.isSuccessful()) {
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<HashMap<String, String>> typeRef =
            new TypeReference<HashMap<String, String>>() {};
        Map<String, String> values = mapper.readValue(task_metadata.body().string(), typeRef);

        String TaskARN = values.get("TaskARN");
        String Cluster = values.get("Cluster");
        String ClusterARN =
            Cluster.startsWith("arn:")
                ? Cluster
                : TaskARN.substring(0, TaskARN.lastIndexOf(":")) + ":cluster/" + Cluster;

        ab.put(ResourceAttributes.AWS_ECS_CLUSTER_ARN, ClusterARN);
        ab.put(ResourceAttributes.AWS_ECS_LAUNCHTYPE, values.get("LaunchType"));
        ab.put(ResourceAttributes.AWS_ECS_TASK_ARN, TaskARN);
        ab.put(ResourceAttributes.AWS_ECS_TASK_FAMILY, values.get("Family"));
        ab.put(ResourceAttributes.AWS_ECS_TASK_REVISION, values.get("Revision"));
      }

      return Resource.create(ab.build());
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    return ecs_resource;
  }
}
