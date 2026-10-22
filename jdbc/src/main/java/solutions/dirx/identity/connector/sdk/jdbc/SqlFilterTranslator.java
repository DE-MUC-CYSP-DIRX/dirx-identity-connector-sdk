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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.model.FilterTranslator;

/**
 * Translates search filters to parameterized SQL conditions. Attribute names are resolved to
 * columns of a {@link JdbcTable}, so only known columns appear in the SQL; values are always
 * passed as parameters.
 *
 * <p>Substring filters become {@code LIKE} conditions with {@code \} as escape character.
 * Approximate filters are not supported.
 */
public class SqlFilterTranslator implements FilterTranslator<SqlFragment> {

    private final JdbcTable table;

    /**
     * Creates a translator.
     *
     * @param table the table whose columns may be referenced
     */
    public SqlFilterTranslator(JdbcTable table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    @Override
    public SqlFragment and(List<SqlFragment> operands) {
        return SqlFragment.join(" AND ", operands);
    }

    @Override
    public SqlFragment or(List<SqlFragment> operands) {
        return SqlFragment.join(" OR ", operands);
    }

    @Override
    public SqlFragment not(SqlFragment operand) {
        return new SqlFragment("NOT (" + operand.sql() + ")", operand.parameters());
    }

    @Override
    public SqlFragment equalTo(String attribute, String value) throws ConnectorException {
        return compare(attribute, "=", value);
    }

    @Override
    public SqlFragment present(String attribute) throws ConnectorException {
        return SqlFragment.of(column(attribute).quotedName() + " IS NOT NULL");
    }

    @Override
    public SqlFragment substring(String attribute, String initial, List<String> any, String fin)
            throws ConnectorException {
        JdbcColumn column = column(attribute);
        StringBuilder pattern = new StringBuilder();
        if (initial != null) {
            pattern.append(escapeLike(initial));
        }
        pattern.append('%');
        for (String part : any) {
            pattern.append(escapeLike(part)).append('%');
        }
        if (fin != null) {
            pattern.append(escapeLike(fin));
        }
        return SqlFragment.of(column.quotedName() + " LIKE ? ESCAPE '\\'", pattern.toString());
    }

    @Override
    public SqlFragment greaterOrEqual(String attribute, String value) throws ConnectorException {
        return compare(attribute, ">=", value);
    }

    @Override
    public SqlFragment lessOrEqual(String attribute, String value) throws ConnectorException {
        return compare(attribute, "<=", value);
    }

    /**
     * Resolves an attribute to a column.
     *
     * @param attribute the attribute name
     * @return the column
     * @throws ConnectorException if the attribute is unknown
     */
    protected JdbcColumn column(String attribute) throws ConnectorException {
        return table.column(attribute);
    }

    private SqlFragment compare(String attribute, String operator, String value) throws ConnectorException {
        JdbcColumn column = column(attribute);
        List<Object> params = new ArrayList<>(1);
        params.add(JdbcValues.toParameter(value, column));
        return new SqlFragment(column.quotedName() + " " + operator + " ?", params);
    }

    /**
     * Escapes the {@code LIKE} wildcards {@code %} and {@code _} and the escape character {@code \}.
     *
     * @param value the literal value
     * @return the escaped value
     */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
