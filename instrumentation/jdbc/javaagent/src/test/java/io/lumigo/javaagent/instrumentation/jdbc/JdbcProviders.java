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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.sql.DataSource;
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

  private static final Map<String, String> jdbcDriverClassNames =
      Map.of(
          "h2", "org.h2.Driver",
          "derby", "org.apache.derby.jdbc.EmbeddedDriver",
          "hsqldb", "org.hsqldb.jdbc.JDBCDriver");

  private static final Map<String, String> jdbcUserNames =
      Map.of(
          "h2", "",
          "derby", "APP",
          "hsqldb", "SA");

  private static final Map<String, Map<String, DataSource>> cpDatasources = new HashMap<>();

  static {
    String[] poolNames = {"hikari", "tomcat", "c3p0"};
    for (String poolName : poolNames) {
      Map<String, DataSource> datasources = new HashMap<>();
      jdbcUrls.forEach(
          (dbType, jdbcUrl) -> {
            try {
              datasources.put(dbType, createDatasource(poolName, dbType, jdbcUrl));
            } catch (PropertyVetoException e) {
              throw new RuntimeException(e);
            }
          });
      cpDatasources.put(poolName, datasources);
    }
  }

  private static DataSource createDatasource(String poolName, String dbType, String jdbcUrl)
      throws PropertyVetoException {
    switch (poolName) {
      case "hikari":
        return createHikariDatasource(dbType, jdbcUrl);
      case "tomcat":
        return createTomcatDatasource(dbType, jdbcUrl);
      case "c3p0":
        return createC3p0Datasource(dbType, jdbcUrl);
      default:
        throw new IllegalArgumentException("Unknown pool name: " + poolName);
    }
  }

  private static DataSource createC3p0Datasource(String dbType, String jdbcUrl)
      throws PropertyVetoException {
    com.mchange.v2.c3p0.ComboPooledDataSource ds = new com.mchange.v2.c3p0.ComboPooledDataSource();
    ds.setJdbcUrl(Objects.equals(dbType, "derby") ? jdbcUrl + ";create=true" : jdbcUrl);
    ds.setDriverClass(jdbcDriverClassNames.get(dbType));
    final String username = jdbcUserNames.get(dbType);
    if (!Objects.equals(username, "")) {
      ds.setUser(username);
    }
    ds.setPassword("");
    ds.setMaxPoolSize(1);
    return ds;
  }

  private static DataSource createTomcatDatasource(String dbType, String jdbcUrl) {
    org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
    ds.setUrl(Objects.equals(dbType, "derby") ? jdbcUrl + ";create=true" : jdbcUrl);
    ds.setDriverClassName(jdbcDriverClassNames.get(dbType));
    final String username = jdbcUserNames.get(dbType);
    if (!Objects.equals(username, "")) {
      ds.setUsername(username);
    }
    ds.setUsername(jdbcUserNames.get(dbType));
    ds.setPassword("");
    ds.setMaxActive(1);
    return ds;
  }

  private static DataSource createHikariDatasource(String dbType, String jdbcUrl) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(Objects.equals(dbType, "derby") ? jdbcUrl + ";create=true" : jdbcUrl);
    final String username = jdbcUserNames.get(dbType);
    if (!Objects.equals(username, "")) {
      config.setUsername(username);
    }
    config.setPassword("");
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.setMaximumPoolSize(1);
    return new HikariDataSource(config);
  }

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
            "SELECT * FROM people"),
        Arguments.of(
            "h2 (hikari)",
            cpDatasources.get("hikari").get("h2").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "derby (hikari)",
            cpDatasources.get("hikari").get("derby").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "hsqldb (hikari)",
            cpDatasources.get("hikari").get("hsqldb").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "h2 (tomcat)",
            cpDatasources.get("tomcat").get("h2").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "derby (tomcat)",
            cpDatasources.get("tomcat").get("derby").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "hsqldb (tomcat)",
            cpDatasources.get("tomcat").get("hsqldb").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "h2 (c3p0)",
            cpDatasources.get("c3p0").get("h2").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "derby (c3p0)",
            cpDatasources.get("c3p0").get("derby").getConnection(),
            "SELECT * FROM people"),
        Arguments.of(
            "hsqldb (c3p0)",
            cpDatasources.get("c3p0").get("hsqldb").getConnection(),
            "SELECT * FROM people"));
  }
}
