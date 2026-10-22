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

/**
 * A piece of SQL with positional ({@code ?}) parameters.
 *
 * @param sql        the SQL text
 * @param parameters the parameter values, in order
 */
public record SqlFragment(String sql, List<Object> parameters) {

    /** Validates the components and copies the parameter list. */
    public SqlFragment {
        Objects.requireNonNull(sql, "sql");
        parameters = parameters == null ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(parameters));
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
