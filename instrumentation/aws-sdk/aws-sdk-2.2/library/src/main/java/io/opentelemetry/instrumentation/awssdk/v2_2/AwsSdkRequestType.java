/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.util.Collections;
import java.util.List;
import java.util.Map;

enum AwsSdkRequestType {
  S3(FieldMapping.request("aws.bucket.name", "Bucket")),
  SQS(FieldMapping.request("aws.queue.url", "QueueUrl"), FieldMapping.request("aws.queue.name", "QueueName")),
  KINESIS(FieldMapping.request("aws.stream.name", "StreamName")),
  DYNAMODB(FieldMapping.request("aws.table.name", "TableName"));

  // Wrapping in unmodifiableMap
  @SuppressWarnings("ImmutableEnumChecker")
  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequestType(FieldMapping... fieldMappings) {
    this.fields = Collections.unmodifiableMap(FieldMapping.groupByType(fieldMappings));
  }

  List<FieldMapping> fields(FieldMapping.Type type) {
    return fields.get(type);
  }
}
