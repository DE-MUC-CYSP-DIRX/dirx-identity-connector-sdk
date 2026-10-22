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

package solutions.dirx.identity.connector.sdk.scim;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import solutions.dirx.identity.connector.sdk.api.model.FilterTranslator;

/**
 * Translates SDK search filters into SCIM filter expressions (RFC 7644, section 3.4.2.2).
 *
 * <p>Values are written as quoted JSON strings, except for attributes for which the
 * {@code nonString} predicate returns {@code true} (for example {@code active}); their values
 * are written verbatim. Substring filters with several parts are translated into a
 * conjunction of {@code sw}, {@code co} and {@code ew} expressions, which may match a superset
 * of the original filter because the order of the parts is not enforced. Approximate filters
 * are not supported.
 */
public class ScimFilterTranslator implements FilterTranslator<String> {

    private final Predicate<String> nonString;

    /** Creates a translator that writes the values of {@code active} unquoted. */
    public ScimFilterTranslator() {
        this(name -> name.equalsIgnoreCase("active"));
    }

    /**
     * Creates a translator.
     *
     * @param nonString tells whether an attribute holds booleans or numbers, whose values are
     *                  written unquoted
     */
    public ScimFilterTranslator(Predicate<String> nonString) {
        this.nonString = Objects.requireNonNull(nonString, "nonString");
    }

    @Override
    public String and(List<String> operands) {
        return "(" + String.join(" and ", operands) + ")";
    }

    @Override
    public String or(List<String> operands) {
        return "(" + String.join(" or ", operands) + ")";
    }

    @Override
    public String not(String operand) {
        return "not(" + operand + ")";
    }

    @Override
    public String equalTo(String attribute, String value) {
        return compare(attribute, "eq", value);
    }

    @Override
    public String present(String attribute) {
        return attribute + " pr";
    }

    @Override
    public String substring(String attribute, String initial, List<String> any, String fin) {
        List<String> parts = new java.util.ArrayList<>();
        if (initial != null && !initial.isEmpty()) {
            parts.add(compare(attribute, "sw", initial));
        }
        for (String a : any) {
            if (!a.isEmpty()) {
                parts.add(compare(attribute, "co", a));
            }
        }
        if (fin != null && !fin.isEmpty()) {
            parts.add(compare(attribute, "ew", fin));
        }
        return parts.size() == 1 ? parts.getFirst() : and(parts);
    }

    @Override
    public String greaterOrEqual(String attribute, String value) {
        return compare(attribute, "ge", value);
    }

    @Override
    public String lessOrEqual(String attribute, String value) {
        return compare(attribute, "le", value);
    }

    private String compare(String attribute, String operator, String value) {
        String v = nonString.test(attribute) ? value : quote(value);
        return attribute + " " + operator + " " + v;
    }

    /**
     * Quotes a value as JSON string.
     *
     * @param value the value
     * @return the quoted value
     */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
