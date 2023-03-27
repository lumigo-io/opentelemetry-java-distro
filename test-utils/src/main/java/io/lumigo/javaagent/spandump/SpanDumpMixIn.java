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
package io.lumigo.javaagent.spandump;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class SpanDumpMixIn {
  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper(new JsonFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET))
          .registerModule(
              new SimpleModule("Span")
                  .addDeserializer(SpanDumpEntry.class, new SpanDumpEntryDeserializer())
                  .addDeserializer(SpanContext.class, new SpanContextDeserializer())
                  .addDeserializer(StatusData.class, new StatusDataDeserializer())
                  .addDeserializer(EventData.class, new EventDeserializer())
                  .addDeserializer(LinkData.class, new LinkDeserializer())
                  .addDeserializer(Attributes.class, new AttributesDeserializer())
                  .addDeserializer(Resource.class, new ResourceDeserializer()));
}

final class SpanDumpEntryDeserializer extends JsonDeserializer<SpanDumpEntry> {
  @Override
  public SpanDumpEntry deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      throw new IllegalArgumentException("Expected object start token, found: " + p.currentToken());
    }

    JsonNode node = p.getCodec().readTree(p);

    if (!node.has("span")) {
      throw new IOException("Expected field 'span' not found");
    }

    if (!node.has("resource")) {
      throw new IOException("Expected field 'resource' not found");
    }

    Span span = ctxt.readTreeAsValue(node.get("span"), Span.class);
    Resource resource = ctxt.readTreeAsValue(node.get("resource"), Resource.class);

    return new SpanDumpEntry(span, resource);
  }
}

final class SpanContextDeserializer extends JsonDeserializer<SpanContext> {
  @Override
  public SpanContext deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      throw new IOException("Expected object start token, found: " + p.currentToken());
    }

    JsonNode node = p.getCodec().readTree(p);

    String traceId = TraceId.getInvalid();
    if (node.has("traceId")) {
      final String t = node.get("traceId").textValue();
      traceId =
          TraceId.fromLongs(
              OtelEncodingUtils.longFromBase16String(t.substring(0, 16), 0),
              OtelEncodingUtils.longFromBase16String(t.substring(16), 0));
    }

    String spanId = SpanId.getInvalid();
    if (node.has("id")) {
      spanId = SpanId.fromLong(OtelEncodingUtils.longFromBase16String(node.get("id").asText(), 0));
    }

    TraceFlags traceFlags = TraceFlags.getDefault();
    if (node.has("traceFlags")) {
      traceFlags = TraceFlags.fromHex(node.get("traceFlags").asText(), 0);
    }

    TraceState traceState = TraceState.getDefault();
    if (node.has("traceState")) {
      TraceStateBuilder traceStateBuilder = TraceState.builder();

      JsonNode traceStateNode = node.get("traceState");
      Iterator<Map.Entry<String, JsonNode>> fields = traceStateNode.fields();
      while (fields.hasNext()) {
        final Map.Entry<String, JsonNode> field = fields.next();
        traceStateBuilder.put(field.getKey(), field.getValue().textValue());
      }
      traceState = traceStateBuilder.build();
    }

    return SpanContext.create(traceId, spanId, traceFlags, traceState);
  }
}

final class StatusDataDeserializer extends JsonDeserializer<StatusData> {
  @Override
  public StatusData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      throw new IOException("Expected object start token, found: " + p.currentToken());
    }

    JsonNode node = p.getCodec().readTree(p);

    StatusCode statusCode = StatusCode.UNSET;
    if (node.has("statusCode")) {
      statusCode = StatusCode.valueOf(node.get("statusCode").asText());
    }

    String description = "";
    if (node.has("description")) {
      description = node.get("description").asText();
    }

    return StatusData.create(statusCode, description);
  }
}

final class ResourceDeserializer extends JsonDeserializer<Resource> {

  @Override
  public Resource deserialize(final JsonParser p, final DeserializationContext ctx)
      throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      throw new IOException("Expected object start token, found: " + p.currentToken());
    }

    JsonNode node = p.getCodec().readTree(p);

    return Resource.create((ctx.readTreeAsValue(node.get("attributes"), Attributes.class)));
  }
}

final class AttributesDeserializer extends JsonDeserializer<Attributes> {

  @Override
  public Attributes deserialize(final JsonParser p, final DeserializationContext ctx)
      throws IOException {
    if (!p.isExpectedStartArrayToken()) {
      throw new IOException("Expected array start token, found: " + p.currentToken());
    }

    final AttributesBuilder attributesBuilder = Attributes.builder();

    final JsonNode attributesNode = p.getCodec().readTree(p);
    for (final JsonNode attributeNode : attributesNode) {
      final String keyName = attributeNode.get("key").textValue();

      final JsonNode valueNode = attributeNode.get("value");
      if (valueNode.has("stringValue")) {
        attributesBuilder.put(keyName, valueNode.get("stringValue").asText());
      } else if (valueNode.has("boolValue")) {
        attributesBuilder.put(keyName, valueNode.get("boolValue").asBoolean());
      } else if (valueNode.has("intValue")) {
        attributesBuilder.put(keyName, valueNode.get("intValue").asLong());
      } else if (valueNode.has("doubleValue")) {
        attributesBuilder.put(keyName, valueNode.get("doubleValue").asDouble());
      } else if (valueNode.has("arrayValue")) {
        final JsonNode arrayNode = valueNode.get("arrayValue");
        final List<Object> values = new ArrayList<>(arrayNode.size());
        final AtomicReference<Class<?>> valueType = new AtomicReference<>();

        /*
         * Don't judge! Do too much TypeScript or Python or Go, and you too will be
         * looking for silly ways to have nested functions in Java.
         */
        final Consumer<Class<?>> updateValueTypeOrThrow =
            (type) -> {
              if (valueType.get() == null) {
                valueType.set(type);
              } else if (valueType.getAndSet(type) != type) {
                throw new IllegalArgumentException("Inconsistent value types in array");
              }
            };

        for (final JsonNode arrayValue : arrayNode) {
          if (valueNode.has("stringValue")) {
            updateValueTypeOrThrow.accept(String.class);
            values.add(valueNode.get("stringValue").textValue());
          } else if (valueNode.has("boolValue")) {
            updateValueTypeOrThrow.accept(Boolean.class);
            values.add(valueNode.get("boolValue").booleanValue());
          } else if (valueNode.has("intValue")) {
            updateValueTypeOrThrow.accept(Long.class);
            values.add(valueNode.get("intValue").intValue());
          } else if (valueNode.has("doubleValue")) {
            updateValueTypeOrThrow.accept(Double.class);
            values.add(valueNode.get("doubleValue").doubleValue());
          } else {
            throw new IllegalArgumentException("Unexpected value type in array: " + arrayValue);
          }
        }

        if (valueType.get() == String.class) {
          attributesBuilder.put(
              AttributeKey.stringArrayKey(keyName),
              values.stream().map(Object::toString).collect(Collectors.toList()));
        } else if (valueType.get() == Boolean.class) {
          attributesBuilder.put(
              AttributeKey.booleanArrayKey(keyName),
              values.stream().map(o -> (Boolean) o).collect(Collectors.toList()));
        } else if (valueType.get() == Double.class) {
          attributesBuilder.put(
              AttributeKey.doubleArrayKey(keyName),
              values.stream().map(o -> (Double) o).collect(Collectors.toList()));
        } else if (valueType.get() == Long.class) {
          attributesBuilder.put(
              AttributeKey.longArrayKey(keyName),
              values.stream().map(o -> (Long) o).collect(Collectors.toList()));
        }
      }
    }

    return attributesBuilder.build();
  }
}

final class EventDeserializer extends JsonDeserializer<EventData> {

  @Override
  public EventData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      throw new IOException("Expected object start token, found: " + p.currentToken());
    }

    final JsonNode node = p.getCodec().readTree(p);

    String name = "";
    if (node.has("name")) {
      name = node.get("name").asText();
    }

    long timestamp = -1L;
    if (node.has("timestamp")) {
      timestamp = node.get("timestamp").longValue();
    }

    Attributes attributes = Attributes.empty();
    if (node.has("attributes")) {
      attributes = ctxt.readTreeAsValue(node.get("attributes"), Attributes.class);
    }

    return EventData.create(timestamp, name, attributes);
  }
}

final class LinkDeserializer extends JsonDeserializer<LinkData> {

  @Override
  public LinkData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      throw new IOException("Expected object start token, found: " + p.currentToken());
    }

    JsonNode node = p.getCodec().readTree(p);

    SpanContext spanContext = SpanContext.getInvalid();
    if (node.has("spanContext")) {
      spanContext = ctxt.readTreeAsValue(node.get("spanContext"), SpanContext.class);
    }

    Attributes attributes = Attributes.empty();
    if (node.has("attributes")) {
      attributes = ctxt.readTreeAsValue(node.get("attributes"), Attributes.class);
    }

    return LinkData.create(spanContext, attributes);
  }
}
