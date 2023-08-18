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
package io.lumigo.javaagent.instrumentation.jdbc;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.MethodSource;

public class JdbcInstrumentationTest {
  @RegisterExtension
  static final AgentInstrumentationExtension instrumentation =
      AgentInstrumentationExtension.create();

  private static final String PEOPLE_RESULT_ROW =
      "[{\"ID\": \"1\", \"FIRST_NAME\": \"John\", \"LAST_NAME\": \"Smith\", \"IMAGE\": \"✂\"}]";

  // @BeforeAll can not be parameterized, which is why we use a method we call at the start of a
  // test.
  private void setupData(Connection connection) throws SQLException {
    // Set up the data
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE people (id INTEGER PRIMARY KEY, first_name VARCHAR(100), last_name VARCHAR(100), image BLOB)");
      statement.execute("INSERT INTO people VALUES (1, 'John', 'Smith', null)");
      statement.execute("INSERT INTO people VALUES (2, 'Jane', 'Doe', null)");
      statement.execute("INSERT INTO people VALUES (3, 'Chuck', 'Norris', null)");
      statement.execute("INSERT INTO people VALUES (4, 'Bruce', 'Lee', null)");
      statement.execute("INSERT INTO people VALUES (5, 'Jackie', 'Chan', null)");
      statement.execute("INSERT INTO people VALUES (6, 'Jet', 'Li', null)");
      statement.execute("INSERT INTO people VALUES (7, 'Arnold', 'Schwarzenegger', null)");
      statement.execute("INSERT INTO people VALUES (8, 'Sylvester', 'Stallone', null)");
    }
  }

  private void teardownData(Connection connection) throws SQLException {
    // Clean up the table
    try (Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE people");
    }
  }

  @ParameterizedTest(name = "{index} => testSelectQueryStatement with {0}")
  @MethodSource("io.lumigo.javaagent.instrumentation.jdbc.JdbcProviders#stream")
  void testQueryStatement(ArgumentsAccessor arguments) throws SQLException {
    setupData(arguments.get(1, Connection.class));

    // Execute SELECT statement
    try (Statement statement = arguments.get(1, Connection.class).createStatement()) {
      instrumentation.runWithSpan(
          "parent",
          () -> {
            try {
              statement.execute(arguments.getString(2));

              // Read the result set
              ResultSet results = statement.getResultSet();
              int count = 0;
              while (results.next()) {
                count++;
                Assertions.assertThat(results.getString(2)).isNotNull();
                Assertions.assertThat(results.getString(3)).isNotNull();
                Assertions.assertThat(results.getBlob(4)).isNull();
              }
              Assertions.assertThat(count).isEqualTo(8);
            } catch (SQLException e) {
              e.printStackTrace();
            }
          });
    }

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                        span ->
                            span.hasName("sql payload")
                                .hasKind(SpanKind.INTERNAL)
                                .hasAttribute(
                                    AttributeKey.stringKey(
                                        ResultSetInstrumentation.SQL_PAYLOAD_ATTRIBUTE_KEY),
                                    PEOPLE_RESULT_ROW)));

    teardownData(arguments.get(1, Connection.class));
  }

  @ParameterizedTest(name = "{index} => testQueryPreparedStatement with {0}")
  @MethodSource("io.lumigo.javaagent.instrumentation.jdbc.JdbcProviders#stream")
  void testQueryPreparedStatement(ArgumentsAccessor arguments) throws SQLException {
    setupData(arguments.get(1, Connection.class));

    // Execute SELECT statement
    try (PreparedStatement statement =
        arguments.get(1, Connection.class).prepareStatement(arguments.getString(2))) {
      instrumentation.runWithSpan(
          "parent",
          () -> {
            try {
              statement.execute();

              // Read the result set
              ResultSet results = statement.getResultSet();
              int count = 0;
              while (results.next()) {
                count++;
                Assertions.assertThat(results.getString(2)).isNotNull();
                Assertions.assertThat(results.getString(3)).isNotNull();
                Assertions.assertThat(results.getBlob(4)).isNull();
              }
              Assertions.assertThat(count).isEqualTo(8);
            } catch (SQLException e) {
              e.printStackTrace();
            }
          });
    }

    assertThat(instrumentation.waitForTraces(1))
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                        span ->
                            span.hasName("sql payload")
                                .hasKind(SpanKind.INTERNAL)
                                .hasAttribute(
                                    AttributeKey.stringKey(
                                        ResultSetInstrumentation.SQL_PAYLOAD_ATTRIBUTE_KEY),
                                    PEOPLE_RESULT_ROW)));

    teardownData(arguments.get(1, Connection.class));
  }
}
