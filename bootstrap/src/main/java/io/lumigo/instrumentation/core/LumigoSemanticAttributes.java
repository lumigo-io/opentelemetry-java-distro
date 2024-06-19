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
package io.lumigo.instrumentation.core;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;

/** Semantic Attribute keys specific to Lumigo. */
public final class LumigoSemanticAttributes {
  public static final AttributeKey<String> HTTP_REQUEST_BODY =
      AttributeKey.stringKey("http.request.body");

  public static final AttributeKey<String> HTTP_RESPONSE_BODY =
      AttributeKey.stringKey("http.response.body");

  public static final AttributeKey<String> MESSAGING_PAYLOAD =
      AttributeKey.stringKey("messaging.message.payload");

  public static final AttributeKey<List<String>> MESSAGING_HEADERS =
      AttributeKey.stringArrayKey("messaging.message.headers");

  public static final AttributeKey<String> GRPC_REQUEST_BODY =
      AttributeKey.stringKey("rpc.grpc.request.payload");

  public static final AttributeKey<String> GRPC_RESPONSE_BODY =
      AttributeKey.stringKey("rpc.grpc.response.payload");

  public static final AttributeKey<String> DB_RESPONSE_BODY =
      AttributeKey.stringKey("db.response.body");
}
