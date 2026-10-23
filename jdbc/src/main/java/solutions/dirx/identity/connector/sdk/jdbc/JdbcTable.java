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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;

/**
 * Describes a table mapped to an object type. Attribute names are the column names; lookups
 * ignore case.
 *
 * @param objectType  the object type
 * @param quotedName  the (schema qualified) table name quoted for use in SQL
 * @param keyColumn   the column holding the object identifier
 * @param columns     all columns, in table order, keyed by lower-case attribute name
 */
public record JdbcTable(String objectType, String quotedName, JdbcColumn keyColumn, Map<String, JdbcColumn> columns) {

    /** Validates the components and copies the column map. */
    public JdbcTable {
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(quotedName, "quotedName");
        Objects.requireNonNull(keyColumn, "keyColumn");
        columns = Collections.unmodifiableMap(new LinkedHashMap<>(columns));
    }

    /**
     * Finds the column of an attribute.
     *
     * @param attributeName the attribute name, case is ignored
     * @return the column, or empty
     */
    public Optional<JdbcColumn> findColumn(String attributeName) {
        return Optional.ofNullable(columns.get(attributeName.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns the column of an attribute.
     *
     * @param attributeName the attribute name, case is ignored
     * @return the column
     * @throws ConnectorException {@link ErrorCode#NO_SUCH_ATTRIBUTE} if the table has no such column
     */
    public JdbcColumn column(String attributeName) throws ConnectorException {
        return findColumn(attributeName).orElseThrow(() -> new ConnectorException(ErrorCode.NO_SUCH_ATTRIBUTE,
                "object type " + objectType + " has no attribute " + attributeName));
    }

    /**
     * Reads the description of a table from the database metadata.
     *
     * @param connection the connection
     * @param objectType the object type
     * @param tableName  the table name, optionally qualified with a schema ({@code SCHEMA.TABLE}); the case
     *                   is matched as given, upper case or lower case
     * @param keyColumn  the key column name, or {@code null} to use the single-column primary key
     * @return the table description
     * @throws SQLException       if reading the metadata fails
     * @throws ConnectorException {@link ErrorCode#CONFIGURATION} if the table or key column cannot be found
     */
    public static JdbcTable load(Connection connection, String objectType, String tableName,
                                 @Nullable String keyColumn)
            throws SQLException, ConnectorException {
        DatabaseMetaData meta = connection.getMetaData();
        String schema = null;
        String table = tableName;
        int dot = tableName.indexOf('.');
        if (dot > 0) {
            schema = tableName.substring(0, dot);
            table = tableName.substring(dot + 1);
        }
        for (String[] candidate : candidates(schema, table)) {
            List<JdbcColumn> columns = readColumns(meta, candidate[0], candidate[1]);
            if (columns.isEmpty()) {
                continue;
            }
            String quote = identifierQuote(meta);
            String quotedTable = (candidate[0] != null ? quote(candidate[0], quote) + "." : "") + quote(candidate[1], quote);
            Map<String, JdbcColumn> byName = new LinkedHashMap<>();
            columns.forEach(c -> byName.put(c.attributeName().toLowerCase(Locale.ROOT), c));
            String keyName = keyColumn != null ? keyColumn : primaryKey(meta, candidate[0], candidate[1], tableName);
            JdbcColumn key = byName.get(keyName.toLowerCase(Locale.ROOT));
            if (key == null) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        "table " + tableName + " has no column " + keyName);
            }
            return new JdbcTable(objectType, quotedTable, key, byName);
        }
        throw new ConnectorException(ErrorCode.CONFIGURATION, "table " + tableName + " not found");
    }

    private static List<String[]> candidates(String schema, String table) {
        List<String[]> list = new ArrayList<>();
        list.add(new String[] {schema, table});
        list.add(new String[] {schema == null ? null : schema.toUpperCase(Locale.ROOT), table.toUpperCase(Locale.ROOT)});
        list.add(new String[] {schema == null ? null : schema.toLowerCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)});
        return list;
    }

    private static List<JdbcColumn> readColumns(DatabaseMetaData meta, String schema, String table) throws SQLException {
        String quote = identifierQuote(meta);
        List<JdbcColumn> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, schema, escapePattern(meta, table), null)) {
            while (rs.next()) {
                if (!table.equals(rs.getString("TABLE_NAME"))) {
                    continue;
                }
                String name = rs.getString("COLUMN_NAME");
                boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                boolean generated = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))
                        || "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN"));
                boolean hasDefault = rs.getString("COLUMN_DEF") != null;
                columns.add(new JdbcColumn(name, name, quote(name, quote), rs.getInt("DATA_TYPE"), nullable, generated, hasDefault));
            }
        }
        return columns;
    }

    private static String primaryKey(DatabaseMetaData meta, String schema, String table, String tableName)
            throws SQLException, ConnectorException {
        List<String> keys = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (keys.size() != 1) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "table " + tableName
                    + " has no single-column primary key; configure the key column explicitly");
        }
        return keys.getFirst();
    }

    private static String escapePattern(DatabaseMetaData meta, String name) throws SQLException {
        String escape = meta.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            return name;
        }
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static String identifierQuote(DatabaseMetaData meta) throws SQLException {
        String quote = meta.getIdentifierQuoteString();
        return quote == null || quote.isBlank() ? "" : quote.strip();
    }

    private static String quote(String name, String quote) {
        return quote.isEmpty() ? name : quote + name.replace(quote, quote + quote) + quote;
    }
}
