/*
 * Copyright 2024 Lumigo LTD
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
package io.lumigo.javaagent.instrumentation.mongo.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoClientTest {

  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private MongoDBContainer mongoDBContainer;
  private MongoClient mongoClient;
  private MongoDatabase database;

  @BeforeEach
  public void setUp() {
    // Start the MongoDB container
    mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:latest"));
    mongoDBContainer.start();

    // Connect to the MongoDB container
    String connectionString = mongoDBContainer.getConnectionString();
    mongoClient = MongoClients.create(connectionString);

    // Initialize the database
    database = mongoClient.getDatabase("testDB");
  }

  @AfterEach
  public void tearDown() {
    // Close the MongoDB client and stop the container
    if (mongoClient != null) {
      mongoClient.close();
    }
    if (mongoDBContainer != null) {
      mongoDBContainer.stop();
    }
  }

  @Test
  public void testMongoDBIntegration() {
    MongoDatabase adminDatabase = mongoClient.getDatabase("admin");

    // Step 1: Run the `isMaster` command
    Document isMasterResult = adminDatabase.runCommand(new Document("isMaster", 1));
    System.out.println("isMaster command result: " + isMasterResult.toJson());
    assertTrue(isMasterResult.containsKey("ismaster"));
    assertTrue(isMasterResult.getBoolean("ismaster"));

    // Step 2: Write a document to a collection
    MongoCollection<Document> collection = database.getCollection("testCollection");
    Document doc = new Document("key", "value").append("timestamp", System.currentTimeMillis());
    collection.insertOne(doc);
    System.out.println("Inserted document: " + doc.toJson());

    // Step 3: Read the document from the collection
    Document retrieved = collection.find().first();
    assertEquals("value", retrieved.getString("key"));
    assertTrue(retrieved.containsKey("timestamp"));
    System.out.println("Retrieved document: " + retrieved.toJson());

    TracesAssert.assertThat(instrumentation.waitForTraces(3))
        .hasSize(3)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasKind(SpanKind.CLIENT)
                              .hasAttribute(AttributeKey.stringKey("db.operation"), "isMaster");
                        }),
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasKind(SpanKind.CLIENT)
                              .hasAttribute(AttributeKey.stringKey("db.operation"), "insert");
                        }),
            trace ->
                trace
                    .hasSize(1)
                    .hasSpansSatisfyingExactly(
                        span -> {
                          span.hasKind(SpanKind.CLIENT)
                              .hasAttribute(AttributeKey.stringKey("db.operation"), "find");
                        }));
  }
}
