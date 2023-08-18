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

import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.h2.Driver;
import org.hsqldb.jdbc.JDBCDriver;
import org.junit.jupiter.params.provider.Arguments;

final class JdbcProviders {
  private static final Map<String, String> jdbcUrls =
      Map.of(
          "h2", "jdbc:h2:mem:jdbc-test",
          "derby", "jdbc:derby:memory:jdbc-test",
          "hsqldb", "jdbc:hsqldb:mem:jdbc-test");

  static Stream<Arguments> stream() throws SQLException {
    return Stream.of(
        Arguments.of("h2", new Driver().connect(jdbcUrls.get("h2"), null), "SELECT * FROM people"),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby") + ";create=true", null),
            "SELECT * FROM people"),
        Arguments.of(
            "hsqldb",
            new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null),
            "SELECT * FROM people"));
  }
}
