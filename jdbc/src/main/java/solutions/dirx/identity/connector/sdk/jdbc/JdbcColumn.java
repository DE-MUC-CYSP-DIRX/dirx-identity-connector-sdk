/*
 * Copyright 2026 Atos Secure Digital GmbH
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
