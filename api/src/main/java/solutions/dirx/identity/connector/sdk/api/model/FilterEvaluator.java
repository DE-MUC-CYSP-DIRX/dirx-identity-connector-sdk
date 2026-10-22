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

package solutions.dirx.identity.connector.sdk.api.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluates a {@link SearchFilter} against a {@link ConnectorObject} in memory.
 *
 * <p>Use it for targets that cannot filter on the server side, for example files or REST
 * APIs without query support. Matching rules:
 * <ul>
 *   <li>Attribute names and string values are compared case-insensitively.</li>
 *   <li>A filter matches a multi-valued attribute if any value matches.</li>
 *   <li>{@code >=} and {@code <=} compare numerically when both sides are numbers,
 *       otherwise lexicographically ignoring case.</li>
 *   <li>Approximate match ignores case and whitespace.</li>
 *   <li>{@link SecretValue} attribute values never match.</li>
 *   <li>A {@code null} filter matches every object.</li>
 * </ul>
 */
public final class FilterEvaluator {

    private FilterEvaluator() {
    }

    /**
     * Tests whether an object matches a filter.
     *
     * @param filter the filter, or {@code null} to match everything
     * @param object the object to test
     * @return {@code true} if the object matches
     */
    public static boolean matches(SearchFilter filter, ConnectorObject object) {
        Objects.requireNonNull(object, "object");
        if (filter == null) {
            return true;
        }
        return switch (filter) {
            case SearchFilter.And and -> and.filters().stream().allMatch(f -> matches(f, object));
            case SearchFilter.Or or -> or.filters().stream().anyMatch(f -> matches(f, object));
            case SearchFilter.Not not -> !matches(not.filter(), object);
            case SearchFilter.Present present -> !values(object, present.attribute()).isEmpty();
            case SearchFilter.Equals eq -> anyValue(object, eq.attribute(), v -> v.equalsIgnoreCase(eq.value()));
            case SearchFilter.Approx ap -> anyValue(object, ap.attribute(),
                    v -> normalize(v).equals(normalize(ap.value())));
            case SearchFilter.GreaterOrEqual ge -> anyValue(object, ge.attribute(), v -> compare(v, ge.value()) >= 0);
            case SearchFilter.LessOrEqual le -> anyValue(object, le.attribute(), v -> compare(v, le.value()) <= 0);
            case SearchFilter.Substring sub -> anyValue(object, sub.attribute(), v -> substringMatches(v, sub));
        };
    }

    private static List<String> values(ConnectorObject object, String attribute) {
        Optional<Attribute> attr = object.attribute(attribute);
        if (attr.isEmpty()) {
            return List.of();
        }
        return attr.get().values().stream()
                .filter(v -> !(v instanceof SecretValue))
                .map(Attribute::asString)
                .toList();
    }

    private static boolean anyValue(ConnectorObject object, String attribute,
                                    java.util.function.Predicate<String> test) {
        return values(object, attribute).stream().anyMatch(test);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static int compare(String actual, String expected) {
        try {
            return new BigDecimal(actual.trim()).compareTo(new BigDecimal(expected.trim()));
        } catch (NumberFormatException e) {
            return actual.compareToIgnoreCase(expected);
        }
    }

    private static boolean substringMatches(String value, SearchFilter.Substring sub) {
        String v = value.toLowerCase(Locale.ROOT);
        int pos = 0;
        if (sub.initial() != null) {
            String initial = sub.initial().toLowerCase(Locale.ROOT);
            if (!v.startsWith(initial)) {
                return false;
            }
            pos = initial.length();
        }
        for (String any : sub.any()) {
            int idx = v.indexOf(any.toLowerCase(Locale.ROOT), pos);
            if (idx < 0) {
                return false;
            }
            pos = idx + any.length();
        }
        if (sub.fin() != null) {
            String fin = sub.fin().toLowerCase(Locale.ROOT);
            return v.length() - fin.length() >= pos && v.endsWith(fin);
        }
        return true;
    }
}
