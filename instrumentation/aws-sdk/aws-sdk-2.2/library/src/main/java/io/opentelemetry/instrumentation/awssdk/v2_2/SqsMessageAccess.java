/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkPojo;

/**
 * Reflective access to aws-sdk-java-sqs class Message.
 *
 * <p>We currently don't have a good pattern of instrumenting a core library with various plugins
 * that need plugin-specific instrumentation - if we accessed the class directly, Muzzle would
 * prevent the entire instrumentation from loading when the plugin isn't available. We need to
 * carefully check this class has all reflection errors result in no-op, and in the future we will
 * hopefully come up with a better pattern.
 *
 * @see <a
 *     href="https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sqs/model/Message.html">SDK
 *     Javadoc</a>
 * @see <a
 *     href="https://github.com/aws/aws-sdk-java-v2/blob/2.2.0/services/sqs/src/main/resources/codegen-resources/service-2.json#L821-L856">Definition
 *     JSON</a>
 */
final class SqsMessageAccess {

  @Nullable private static final MethodHandle GET_ATTRIBUTES;
  @Nullable private static final MethodHandle GET_MESSAGE_ATTRIBUTES;

  static {
    Class<?> messageClass = null;
    try {
      messageClass = Class.forName("software.amazon.awssdk.services.sqs.model.Message");
    } catch (Throwable t) {
      // Ignore.
    }
    if (messageClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle getAttributes = null;
      try {
        getAttributes =
            lookup.findVirtual(messageClass, "attributesAsStrings", methodType(Map.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      GET_ATTRIBUTES = getAttributes;

      MethodHandle getMessageAttributes = null;
      try {
        getMessageAttributes =
            lookup.findVirtual(messageClass, "messageAttributes", methodType(Map.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      GET_MESSAGE_ATTRIBUTES = getMessageAttributes;
    } else {
      GET_ATTRIBUTES = null;
      GET_MESSAGE_ATTRIBUTES = null;
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, String> getAttributes(Object message) {
    if (GET_ATTRIBUTES == null) {
      return Collections.emptyMap();
    }
    try {
      return (Map<String, String>) GET_ATTRIBUTES.invoke(message);
    } catch (Throwable t) {
      return Collections.emptyMap();
    }
  }

  private SqsMessageAccess() {}

  @SuppressWarnings("unchecked")
  public static Map<String, SdkPojo> getMessageAttributes(Object message) {
    if (GET_MESSAGE_ATTRIBUTES == null) {
      return Collections.emptyMap();
    }
    try {
      return (Map<String, SdkPojo>) GET_MESSAGE_ATTRIBUTES.invoke(message);
    } catch (Throwable t) {
      return Collections.emptyMap();
    }
  }
}
