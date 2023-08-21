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

import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public final class SqlUtility {
  public static final String JSON_TRUNCATION_MARKER = "✂";

  public static final int ATTRIBUTE_VALUE_MAX_LENGTH;

  static {
    ATTRIBUTE_VALUE_MAX_LENGTH =
        InstrumentationConfig.get()
            .getInt("otel.instrumentation.jdbc.attribute-value-max-length", Integer.MAX_VALUE);
  }

  private SqlUtility() {}

  public static String getRowObject(
      ResultSet resultSet, ResultSetMetaData metaData, Integer remainingLength)
      throws SQLException {
    StringBuilder row = new StringBuilder();
    row.append("{");
    boolean limitReached;

    for (int i = 1; i <= metaData.getColumnCount(); i++) {
      switch (metaData.getColumnType(i)) {
        case Types.BIGINT:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getLong(i)),
                  remainingLength);
          break;
        case Types.BOOLEAN:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getBoolean(i)),
                  remainingLength);
          break;
        case Types.ARRAY:
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.BLOB:
        case Types.CLOB:
        case Types.NCLOB:
          limitReached =
              doAppend(row, metaData.getColumnName(i), JSON_TRUNCATION_MARKER, remainingLength);
          break;
        case Types.DOUBLE:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getDouble(i)),
                  remainingLength);
          break;
        case Types.FLOAT:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getFloat(i)),
                  remainingLength);
          break;
        case Types.INTEGER:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getInt(i)),
                  remainingLength);
          break;
        case Types.DECIMAL:
        case Types.NUMERIC:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getBigDecimal(i)),
                  remainingLength);
          break;
        case Types.CHAR:
        case Types.NCHAR:
        case Types.NVARCHAR:
        case Types.VARCHAR:
        case Types.LONGNVARCHAR:
        case Types.LONGVARCHAR:
          limitReached =
              doAppend(row, metaData.getColumnName(i), resultSet.getString(i), remainingLength);
          break;
        case Types.TINYINT:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getByte(i)),
                  remainingLength);
          break;
        case Types.SMALLINT:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getShort(i)),
                  remainingLength);
          break;
        case Types.DATE:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getDate(i)),
                  remainingLength);
          break;
        case Types.TIME:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getTime(i)),
                  remainingLength);
          break;
        case Types.TIMESTAMP:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  String.valueOf(resultSet.getTimestamp(i)),
                  remainingLength);
          break;
        default:
          limitReached =
              doAppend(
                  row,
                  metaData.getColumnName(i),
                  resultSet.getObject(i).toString(),
                  remainingLength);
      }
      if (row.length() > remainingLength || limitReached) {
        break;
      }

      if (i != metaData.getColumnCount()) {
        row.append(", ");
      }
    }
    row.append("}");

    return row.toString();
  }

  private static boolean doAppend(
      StringBuilder row, String columnName, String columnValue, Integer remainingLength) {
    if ((columnName.length() + columnValue.length() + 10) < remainingLength) {
      row.append(buildColumn(columnName, columnValue));
      return false;
    } else {
      if (columnName.length() + 11 < remainingLength) {
        row.append(buildColumn(columnName, JSON_TRUNCATION_MARKER));
      }
      return true;
    }
  }

  public static String constructJsonArray(List<String> rowData) {
    StringBuilder array = new StringBuilder();
    array.append("[");

    for (int i = 0; i < rowData.size(); i++) {
      array.append(rowData.get(i));
      if (i != rowData.size() - 1) {
        array.append(", ");
      }
    }
    array.append("]");

    return array.toString();
  }

  private static String buildColumn(String columnName, String columnValue) {
    return "\"" + columnName + "\": \"" + columnValue + "\"";
  }
}
