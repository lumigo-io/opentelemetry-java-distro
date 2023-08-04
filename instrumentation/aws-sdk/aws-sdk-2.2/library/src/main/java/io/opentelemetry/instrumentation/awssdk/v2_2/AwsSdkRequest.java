/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;

/**
 * Temporary solution - maps only DynamoDB attributes. Final solution should be generated from AWS
 * SDK automatically
 * (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2291).
 */
// We match the actual name in the AWS SDK for better consistency with it and possible future
// autogeneration.
@SuppressWarnings("MemberName")
enum AwsSdkRequest {
  // generic requests
  DynamoDbRequest(AwsSdkRequestType.DYNAMODB, "DynamoDbRequest"),
  S3Request(AwsSdkRequestType.S3, "S3Request"),
  SqsRequest(AwsSdkRequestType.SQS, "SqsRequest"),
  KinesisRequest(AwsSdkRequestType.KINESIS, "KinesisRequest"),
  // specific requests
  BatchGetItem(
      AwsSdkRequestType.DYNAMODB,
      "BatchGetItemRequest",
      FieldMapping.request("aws.dynamodb.table_names", "RequestItems"),
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity")),
  BatchWriteItem(
      AwsSdkRequestType.DYNAMODB,
      "BatchWriteItemRequest",
      FieldMapping.request("aws.dynamodb.table_names", "RequestItems"),
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity"),
      FieldMapping.response("aws.dynamodb.item_collection_metrics", "ItemCollectionMetrics")),
  CreateTable(
      AwsSdkRequestType.DYNAMODB,
      "CreateTableRequest",
      FieldMapping.request("aws.dynamodb.global_secondary_indexes", "GlobalSecondaryIndexes"),
      FieldMapping.request("aws.dynamodb.local_secondary_indexes", "LocalSecondaryIndexes"),
      FieldMapping.request(
          "aws.dynamodb.provisioned_throughput.read_capacity_units",
          "ProvisionedThroughput.ReadCapacityUnits"),
      FieldMapping.request(
          "aws.dynamodb.provisioned_throughput.write_capacity_units",
          "ProvisionedThroughput.WriteCapacityUnits")),
  DeleteItem(
      AwsSdkRequestType.DYNAMODB,
      "DeleteItemRequest",
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity"),
      FieldMapping.response("aws.dynamodb.item_collection_metrics", "ItemCollectionMetrics")),
  GetItem(
      AwsSdkRequestType.DYNAMODB,
      "GetItemRequest",
      FieldMapping.request("aws.dynamodb.projection_expression", "ProjectionExpression"),
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity"),
      FieldMapping.request("aws.dynamodb.consistent_read", "ConsistentRead")),
  ListTables(
      AwsSdkRequestType.DYNAMODB,
      "ListTablesRequest",
      FieldMapping.request("aws.dynamodb.exclusive_start_table_name", "ExclusiveStartTableName"),
      FieldMapping.response("aws.dynamodb.table_count", "TableNames"),
      FieldMapping.request("aws.dynamodb.limit", "Limit")),
  PutItem(
      AwsSdkRequestType.DYNAMODB,
      "PutItemRequest",
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity"),
      FieldMapping.response("aws.dynamodb.item_collection_metrics", "ItemCollectionMetrics")),
  Query(
      AwsSdkRequestType.DYNAMODB,
      "QueryRequest",
      FieldMapping.request("aws.dynamodb.attributes_to_get", "AttributesToGet"),
      FieldMapping.request("aws.dynamodb.consistent_read", "ConsistentRead"),
      FieldMapping.request("aws.dynamodb.index_name", "IndexName"),
      FieldMapping.request("aws.dynamodb.limit", "Limit"),
      FieldMapping.request("aws.dynamodb.projection_expression", "ProjectionExpression"),
      FieldMapping.request("aws.dynamodb.scan_index_forward", "ScanIndexForward"),
      FieldMapping.request("aws.dynamodb.select", "Select"),
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity")),
  Scan(
      AwsSdkRequestType.DYNAMODB,
      "ScanRequest",
      FieldMapping.request("aws.dynamodb.attributes_to_get", "AttributesToGet"),
      FieldMapping.request("aws.dynamodb.consistent_read", "ConsistentRead"),
      FieldMapping.request("aws.dynamodb.index_name", "IndexName"),
      FieldMapping.request("aws.dynamodb.limit", "Limit"),
      FieldMapping.request("aws.dynamodb.projection_expression", "ProjectionExpression"),
      FieldMapping.request("aws.dynamodb.segment", "Segment"),
      FieldMapping.request("aws.dynamodb.select", "Select"),
      FieldMapping.request("aws.dynamodb.total_segments", "TotalSegments"),
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity"),
      FieldMapping.response("aws.dynamodb.count", "Count"),
      FieldMapping.response("aws.dynamodb.scanned_count", "ScannedCount")),
  UpdateItem(
      AwsSdkRequestType.DYNAMODB,
      "UpdateItemRequest",
      FieldMapping.response("aws.dynamodb.consumed_capacity", "ConsumedCapacity"),
      FieldMapping.response("aws.dynamodb.item_collection_metrics", "ItemCollectionMetrics")),
  UpdateTable(
      AwsSdkRequestType.DYNAMODB,
      "UpdateTableRequest",
      FieldMapping.request("aws.dynamodb.attribute_definitions", "AttributeDefinitions"),
      FieldMapping.request("aws.dynamodb.global_secondary_index_updates", "GlobalSecondaryIndexUpdates"),
      FieldMapping.request(
          "aws.dynamodb.provisioned_throughput.read_capacity_units",
          "ProvisionedThroughput.ReadCapacityUnits"),
      FieldMapping.request(
          "aws.dynamodb.provisioned_throughput.write_capacity_units",
          "ProvisionedThroughput.WriteCapacityUnits"));

  private final AwsSdkRequestType type;
  private final String requestClass;

  // Wrap in unmodifiableMap
  @SuppressWarnings("ImmutableEnumChecker")
  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequest(AwsSdkRequestType type, String requestClass, FieldMapping... fields) {
    this.type = type;
    this.requestClass = requestClass;
    this.fields = Collections.unmodifiableMap(FieldMapping.groupByType(fields));
  }

  @Nullable
  static AwsSdkRequest ofSdkRequest(SdkRequest request) {
    // try request type
    AwsSdkRequest result = ofType(request.getClass().getSimpleName());
    // try parent - generic
    if (result == null) {
      result = ofType(request.getClass().getSuperclass().getSimpleName());
    }
    return result;
  }

  private static AwsSdkRequest ofType(String typeName) {
    for (AwsSdkRequest type : values()) {
      if (type.requestClass.equals(typeName)) {
        return type;
      }
    }
    return null;
  }

  List<FieldMapping> fields(FieldMapping.Type type) {
    return fields.get(type);
  }

  AwsSdkRequestType type() {
    return type;
  }
}
