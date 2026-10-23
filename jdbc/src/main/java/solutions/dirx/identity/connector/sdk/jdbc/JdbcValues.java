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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * Converts between attribute values and JDBC values.
 *
 * <p>Attribute values are {@link String}, {@code byte[]}, {@link Boolean}, {@link Integer}, {@link Long}
 * or {@link SecretValue}. Strings written to numeric or boolean columns are parsed. Values read from
 * the database keep these types where possible; other types (decimals, dates, timestamps) are
 * returned as strings in their SQL literal form.
 */
public final class JdbcValues {

    private JdbcValues() {
    }

    /**
     * Converts an attribute value to a statement parameter for a column.
     *
     * @param value  the attribute value, may be {@code null}
     * @param column the target column
     * @return the parameter value
     * @throws ConnectorException {@link ErrorCode#SCHEMA_VIOLATION} if the value cannot be converted
     */
    public static @Nullable Object toParameter(@Nullable Object value, JdbcColumn column) throws ConnectorException {
        if (value == null) {
            return null;
        }
        if (value instanceof SecretValue secret) {
            return secret.reveal();
        }
        if (!(value instanceof String s)) {
            return value;
        }
        try {
            if (column.isIntegral()) {
                return Long.valueOf(s.strip());
            }
            if (column.isDecimal()) {
                return new BigDecimal(s.strip());
            }
        } catch (NumberFormatException e) {
            throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION,
                    "attribute " + column.attributeName() + " requires a number: " + s, e);
        }
        if (column.isBoolean()) {
            if (!s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false")) {
                throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION,
                        "attribute " + column.attributeName() + " requires true or false: " + s);
            }
            return Boolean.valueOf(s);
        }
        return s;
    }

    /**
     * Reads a column of the current row as attribute value.
     *
     * @param rs     the result set
     * @param index  the column index (1-based)
     * @return the value, or {@code null} if the column is {@code NULL}
     * @throws SQLException if reading fails
     */
    public static @Nullable Object read(ResultSet rs, int index) throws SQLException {
        Object value = rs.getObject(index);
        if (value == null) {
            return null;
        }
        try {
            return switch (value) {
                case String s -> s;
                case Boolean b -> b;
                case Integer i -> i;
                case Long l -> l;
                case Short s -> (int) s;
                case Byte b -> (int) b;
                case byte[] bytes -> bytes;
                case BigInteger bi -> bi.bitLength() < 64 ? (Object) bi.longValue() : bi.toString();
                case BigDecimal bd -> fromDecimal(bd);
                case Timestamp ts -> ts.toLocalDateTime().toString();
                case Clob clob -> readClob(clob);
                case Blob blob -> readBlob(blob);
                default -> Attribute.asString(value);
            };
        } catch (IOException e) {
            throw new SQLException("cannot read LOB value", e);
        }
    }

    private static Object fromDecimal(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return stripped.longValueExact();
            } catch (ArithmeticException e) {
                return stripped.toPlainString();
            }
        }
        return stripped.toPlainString();
    }

    private static String readClob(Clob clob) throws SQLException, IOException {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            for (int n; (n = reader.read(buffer)) >= 0; ) {
                sb.append(buffer, 0, n);
            }
            return sb.toString();
        } finally {
            clob.free();
        }
    }

    private static byte[] readBlob(Blob blob) throws SQLException, IOException {
        try (InputStream in = blob.getBinaryStream()) {
            return in.readAllBytes();
        } finally {
            blob.free();
        }
    }
}
