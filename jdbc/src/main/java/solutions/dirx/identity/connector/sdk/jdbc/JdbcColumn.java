/*
 * Copyright 2026 Atos Secure Digital GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package solutions.dirx.identity.connector.sdk.jdbc;

import java.sql.Types;
import java.util.Objects;

/**
 * Describes a column of a mapped table.
 *
 * @param attributeName the attribute name exposed by the connector
 * @param columnName    the column name as reported by the database
 * @param quotedName    the column name quoted for use in SQL
 * @param sqlType       the JDBC type, see {@link Types}
 * @param nullable      whether the column accepts {@code NULL}
 * @param generated     whether the database generates the value (auto increment, generated column)
 * @param hasDefault    whether the column has a default value
 */
public record JdbcColumn(String attributeName, String columnName, String quotedName, int sqlType,
                         boolean nullable, boolean generated, boolean hasDefault) {

    /** Validates the components. */
    public JdbcColumn {
        Objects.requireNonNull(attributeName, "attributeName");
        Objects.requireNonNull(columnName, "columnName");
        Objects.requireNonNull(quotedName, "quotedName");
    }

    /**
     * Tells whether the column holds numbers without fraction.
     *
     * @return {@code true} for integral types
     */
    public boolean isIntegral() {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> true;
            default -> false;
        };
    }

    /**
     * Tells whether the column holds decimal or floating point numbers.
     *
     * @return {@code true} for decimal types
     */
    public boolean isDecimal() {
        return switch (sqlType) {
            case Types.DECIMAL, Types.NUMERIC, Types.REAL, Types.FLOAT, Types.DOUBLE -> true;
            default -> false;
        };
    }

    /**
     * Tells whether the column holds booleans.
     *
     * @return {@code true} for boolean types
     */
    public boolean isBoolean() {
        return sqlType == Types.BOOLEAN || sqlType == Types.BIT;
    }

    /**
     * Tells whether the column holds binary data.
     *
     * @return {@code true} for binary types
     */
    public boolean isBinary() {
        return switch (sqlType) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> true;
            default -> false;
        };
    }
}
