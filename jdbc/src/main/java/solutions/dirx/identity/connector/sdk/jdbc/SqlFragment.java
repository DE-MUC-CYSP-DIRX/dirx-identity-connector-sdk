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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A piece of SQL with positional ({@code ?}) parameters.
 *
 * @param sql        the SQL text
 * @param parameters the parameter values, in order
 */
public record SqlFragment(String sql, List<@Nullable Object> parameters) {

    /** Validates the components and copies the parameter list. */
    public SqlFragment(String sql, @Nullable List<@Nullable Object> parameters) {
        Objects.requireNonNull(sql, "sql");
        this.sql = sql;
        this.parameters = parameters == null
                ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    /**
     * Creates a fragment.
     *
     * @param sql        the SQL text
     * @param parameters the parameter values
     * @return the fragment
     */
    public static SqlFragment of(String sql, Object... parameters) {
        return new SqlFragment(sql, java.util.Arrays.asList(parameters));
    }

    /**
     * Joins fragments with a separator and wraps the result in parentheses.
     *
     * @param separator the separator, e.g. {@code " AND "}
     * @param fragments the fragments
     * @return the joined fragment
     */
    public static SqlFragment join(String separator, List<SqlFragment> fragments) {
        StringBuilder sql = new StringBuilder("(");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            if (i > 0) {
                sql.append(separator);
            }
            sql.append(fragments.get(i).sql());
            params.addAll(fragments.get(i).parameters());
        }
        return new SqlFragment(sql.append(')').toString(), params);
    }
}
