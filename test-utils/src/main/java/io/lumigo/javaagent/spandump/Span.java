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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable representation of all data collected by the {@link io.opentelemetry.api.trace.Span}
 * class.
 */
@Immutable
@AutoValue
@JsonDeserialize(builder = AutoValue_Span.Builder.class)
public abstract class Span {

  public static Builder builder() {
    return new AutoValue_Span.Builder()
        .setLinks(Collections.emptyList())
        .setAttributes(Attributes.empty())
        .setEvents(Collections.emptyList());
  }

  Span() {}

  public abstract String getName();

  public abstract String getTraceId();

  public abstract String getSpanId();

  public abstract String getParentSpanId();

  public abstract long getStartTimeUnixNano();

  public abstract long getEndTimeUnixNano();

  public abstract StatusData getStatus();

  public abstract SpanKind getKind();

  public abstract Attributes getAttributes();

  public abstract List<EventData> getEvents();

  public abstract List<LinkData> getLinks();

  @AutoValue.Builder
  public abstract static class Builder {

    abstract List<EventData> getEvents();

    abstract List<LinkData> getLinks();

    @JsonProperty("traceId")
    public abstract Builder setTraceId(String traceId);

    @JsonProperty("spanId")
    public abstract Builder setSpanId(String spanId);

    @JsonProperty("parentSpanId")
    public abstract Builder setParentSpanId(String parentSpanId);

    @JsonProperty("name")
    public abstract Builder setName(String name);

    @JsonProperty("startTimeUnixNano")
    public abstract Builder setStartTimeUnixNano(long epochNanos);

    @JsonProperty("endTimeUnixNano")
    public abstract Builder setEndTimeUnixNano(long epochNanos);

    @JsonProperty("status")
    public abstract Builder setStatus(StatusData status);

    @JsonProperty("kind")
    public abstract Builder setKind(SpanKind kind);

    @JsonProperty("attributes")
    public abstract Builder setAttributes(Attributes attributes);

    @JsonProperty("events")
    public abstract Builder setEvents(List<EventData> events);

    @JsonProperty("links")
    public abstract Builder setLinks(List<LinkData> links);

    abstract Span autoBuild();

    public Span build() {
      // make unmodifiable copies of any collections
      setEvents(Collections.unmodifiableList(new ArrayList<>(getEvents())));
      setLinks(Collections.unmodifiableList(new ArrayList<>(getLinks())));
      return autoBuild();
    }
  }
}
