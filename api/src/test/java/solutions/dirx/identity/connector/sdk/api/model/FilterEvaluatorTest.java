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

package solutions.dirx.identity.connector.sdk.api.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilterEvaluatorTest {

    private final ConnectorObject user = ConnectorObject.builder("user", Identifier.of("1"))
            .attribute("cn", "John Smith")
            .attribute("mail", "john@example.com", "js@example.com")
            .attribute("age", 42)
            .attribute("password", SecretValue.of("secret"))
            .build();

    @Test
    void evaluatesSimpleFilters() {
        assertTrue(FilterEvaluator.matches(null, user));
        assertTrue(FilterEvaluator.matches(Filters.eq("CN", "john smith"), user));
        assertTrue(FilterEvaluator.matches(Filters.eq("mail", "js@example.com"), user));
        assertFalse(FilterEvaluator.matches(Filters.eq("cn", "John"), user));
        assertTrue(FilterEvaluator.matches(Filters.present("mail"), user));
        assertFalse(FilterEvaluator.matches(Filters.present("sn"), user));
        assertTrue(FilterEvaluator.matches(Filters.approx("cn", "johnsmith"), user));
    }

    @Test
    void evaluatesSubstringAndOrdering() {
        assertTrue(FilterEvaluator.matches(Filters.startsWith("cn", "jo"), user));
        assertTrue(FilterEvaluator.matches(Filters.endsWith("cn", "SMITH"), user));
        assertTrue(FilterEvaluator.matches(Filters.contains("cn", "n s"), user));
        assertFalse(FilterEvaluator.matches(Filters.contains("cn", "xyz"), user));
        assertFalse(FilterEvaluator.matches(new SearchFilter.Substring("cn", "john", java.util.List.of(), "ohn"),
                user));
        assertTrue(FilterEvaluator.matches(Filters.ge("age", "9"), user));
        assertFalse(FilterEvaluator.matches(Filters.le("age", "9"), user));
        assertTrue(FilterEvaluator.matches(Filters.le("cn", "K"), user));
    }

    @Test
    void evaluatesCompositeFiltersAndIgnoresSecrets() {
        assertTrue(FilterEvaluator.matches(Filters.and(Filters.present("cn"),
                Filters.or(Filters.eq("age", "1"), Filters.eq("age", "42"))), user));
        assertFalse(FilterEvaluator.matches(Filters.not(Filters.present("cn")), user));
        assertFalse(FilterEvaluator.matches(Filters.present("password"), user));
    }
}
