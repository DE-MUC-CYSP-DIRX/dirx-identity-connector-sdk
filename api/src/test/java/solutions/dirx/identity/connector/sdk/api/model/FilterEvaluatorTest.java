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
