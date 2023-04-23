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

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.SelfDescribing;
import org.hamcrest.TypeSafeMatcher;

public class SpanMatchers {

  private SpanMatchers() {}

  private static class SpanDumpEntryMatcher<T> extends TypeSafeMatcher<SpanDumpEntry> {
    private final String name;
    private final Function<SpanDumpEntry, T> spanAccessor;
    private final T expected;

    protected SpanDumpEntryMatcher(
        String name, Function<SpanDumpEntry, T> spanAccessor, T expected) {
      this.name = name;
      this.spanAccessor = spanAccessor;
      this.expected = expected;
    }

    @Override
    protected boolean matchesSafely(final SpanDumpEntry actual) {
      return expected.equals(spanAccessor.apply(actual));
    }

    @Override
    public void describeTo(final Description description) {
      description.appendText("has " + name + ": " + expected);
    }

    @Override
    protected void describeMismatchSafely(SpanDumpEntry item, Description mismatchDescription) {
      mismatchDescription.appendText(
          String.format(
              "should have '%s' as %s; instead, it has: %s",
              expected, name, this.spanAccessor.apply(item)));
    }
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasSpanName(final String expected) {
    return new SpanDumpEntryMatcher<>(
        "span name", (SpanDumpEntry entry) -> entry.getSpan().getName(), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasTraceId(final String expected) {
    return new SpanDumpEntryMatcher<>(
        "trace id", (SpanDumpEntry entry) -> entry.getSpan().getTraceId(), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasParentSpanId(final String expected) {
    return new SpanDumpEntryMatcher<>(
        "parent span id", entry -> entry.getSpan().getParentSpanId().orElse(null), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasSpanId(final String expected) {
    return new SpanDumpEntryMatcher<>(
        "parent span id", (SpanDumpEntry entry) -> entry.getSpan().getSpanId(), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasSpanKind(final SpanKind expected) {
    return new SpanDumpEntryMatcher<>(
        "span kind", (SpanDumpEntry entry) -> entry.getSpan().getKind(), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasStartTime(final long expected) {
    return new SpanDumpEntryMatcher<>(
        "span start time", entry -> entry.getSpan().getStartTimeUnixNano(), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasEndTime(final long expected) {
    return new SpanDumpEntryMatcher<>(
        "span end time", entry -> entry.getSpan().getEndTimeUnixNano(), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasSpanStatus(final StatusData expected) {
    return new SpanDumpEntryMatcher<>(
        "span status", entry -> entry.getSpan().getStatus().orElse(null), expected);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttribute(
      final String attributeKey, String value) {
    return hasAttribute(stringKey(attributeKey), value);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttribute(
      final String attributeKey, boolean value) {
    return hasAttribute(booleanKey(attributeKey), value);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttribute(final String attributeKey, long value) {
    return hasAttribute(longKey(attributeKey), value);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttribute(
      final String attributeKey, double value) {
    return hasAttribute(doubleKey(attributeKey), value);
  }

  public static <T> TypeSafeMatcher<SpanDumpEntry> hasAttribute(
      final AttributeKey<T> attributeKey, T value) {
    return new TypeSafeMatcher<SpanDumpEntry>() {
      @Override
      protected boolean matchesSafely(final SpanDumpEntry actual) {
        return value.equals(actual.getSpan().getAttributes().get(attributeKey));
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText(
            "has a span attribute with key '" + attributeKey + "' and value '" + value + "'");
      }

      @Override
      protected void describeMismatchSafely(SpanDumpEntry item, Description mismatchDescription) {
        mismatchDescription.appendText(
            String.format(
                "should have a span attribute with key '%s' and value '%s'", attributeKey, value));

        final Attributes attributes = item.getSpan().getAttributes();
        final T value = attributes.get(attributeKey);
        if (value != null) {
          mismatchDescription.appendText(String.format(", but the actual value is: '%s'", value));
        } else {
          mismatchDescription.appendList(
              ", but the key does not exist in the span attributes; existing attribute keys: '",
              "', '",
              "'",
              item.getSpan().getAttributes().asMap().keySet().stream()
                  .map(SpanMatchers::toSelfDescribing)
                  .collect(Collectors.toList()));
        }
      }
    };
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttributeOfTypeString(final String attributeKey) {
    return hasAttribute(stringKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttributeOfTypeBoolean(
      final String attributeKey) {
    return hasAttribute(booleanKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttributeOfTypeLong(final String attributeKey) {
    return hasAttribute(longKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasAttributeOfTypeDouble(final String attributeKey) {
    return hasAttribute(doubleKey(attributeKey));
  }

  public static <T> TypeSafeMatcher<SpanDumpEntry> hasAttribute(
      final AttributeKey<T> attributeKey) {
    return new TypeSafeMatcher<SpanDumpEntry>() {
      @Override
      protected boolean matchesSafely(final SpanDumpEntry actual) {
        return actual.getSpan().getAttributes().asMap().containsKey(attributeKey);
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("has a span attribute with key '" + attributeKey + "'");
      }

      @Override
      protected void describeMismatchSafely(SpanDumpEntry item, Description mismatchDescription) {
        mismatchDescription
            .appendText(String.format("should have a span attribute with key '%s'; ", attributeKey))
            .appendList(
                "existing attribute keys: '",
                "', '",
                "'",
                item.getSpan().getAttributes().asMap().keySet().stream()
                    .map(SpanMatchers::toSelfDescribing)
                    .collect(Collectors.toList()));
      }
    };
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttribute(
      final String attributeKey, String value) {
    return hasResourceAttribute(stringKey(attributeKey), value);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttribute(
      final String attributeKey, boolean value) {
    return hasResourceAttribute(booleanKey(attributeKey), value);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttribute(
      final String attributeKey, long value) {
    return hasResourceAttribute(longKey(attributeKey), value);
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttribute(
      final String attributeKey, double value) {
    return hasResourceAttribute(doubleKey(attributeKey), value);
  }

  public static <T> TypeSafeMatcher<SpanDumpEntry> hasResourceAttribute(
      final AttributeKey<T> attributeKey, T value) {
    return new TypeSafeMatcher<SpanDumpEntry>() {
      @Override
      protected boolean matchesSafely(final SpanDumpEntry actual) {
        return value.equals(actual.getResource().getAttributes().get(attributeKey));
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText(
            "has a resource attribute with key '" + attributeKey + "' and value '" + value + "'");
      }

      @Override
      protected void describeMismatchSafely(SpanDumpEntry item, Description mismatchDescription) {
        mismatchDescription.appendText(
            "should have a resource attribute with key '" + attributeKey + "'");

        final Attributes attributes = item.getSpan().getAttributes();
        final T value = attributes.get(attributeKey);
        if (value != null) {
          mismatchDescription.appendText(String.format(", but the actual value is: '%s'", value));
        } else {
          mismatchDescription.appendList(
              ", but the key does not exist in the resource attributes; existing attribute keys: '",
              "', '",
              "'",
              item.getResource().getAttributes().asMap().keySet().stream()
                  .map(SpanMatchers::toSelfDescribing)
                  .collect(Collectors.toList()));
        }
      }
    };
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttributeOfTypeString(
      final String attributeKey) {
    return hasResourceAttribute(stringKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttributeOfTypeBoolean(
      final String attributeKey) {
    return hasResourceAttribute(booleanKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttributeOfTypeLong(
      final String attributeKey) {
    return hasResourceAttribute(longKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttributeOfTypeDouble(
      final String attributeKey) {
    return hasResourceAttribute(doubleKey(attributeKey));
  }

  public static TypeSafeMatcher<SpanDumpEntry> hasResourceAttribute(
      final AttributeKey<?> attributeKey) {
    return new TypeSafeMatcher<SpanDumpEntry>() {
      @Override
      protected boolean matchesSafely(final SpanDumpEntry actual) {
        return actual.getResource().getAttributes().asMap().containsKey(attributeKey);
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("has a resource attribute with key '" + attributeKey + "'");
      }

      @Override
      protected void describeMismatchSafely(SpanDumpEntry item, Description mismatchDescription) {
        mismatchDescription
            .appendText("should have a span attribute with key '" + attributeKey + "'; ")
            .appendList(
                "existing attribute keys: '",
                "', '",
                "'",
                item.getResource().getAttributes().asMap().keySet().stream()
                    .map(SpanMatchers::toSelfDescribing)
                    .collect(Collectors.toList()));
      }
    };
  }

  private static SelfDescribing toSelfDescribing(final AttributeKey<?> key) {
    return description -> description.appendText(key.getKey());
  }
}
