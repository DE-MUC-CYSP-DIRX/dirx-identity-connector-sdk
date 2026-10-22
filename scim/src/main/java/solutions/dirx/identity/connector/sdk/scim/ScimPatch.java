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

package solutions.dirx.identity.connector.sdk.scim;

import java.util.List;
import java.util.function.Predicate;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.rest.Json;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds SCIM PATCH requests (RFC 7644, section 3.5.2) and converts attribute values.
 *
 * <p>Complex values of multi-valued attributes (for example {@code emails}) are represented in
 * the SDK as JSON object strings such as <code>{"value":"a@example.com","type":"work"}</code>;
 * they are sent as JSON objects. The mapping of modifications is:
 * <ul>
 *   <li>{@code ADD} becomes an {@code add} operation;</li>
 *   <li>{@code REPLACE} becomes a {@code replace} operation, or {@code remove} without values;</li>
 *   <li>{@code DELETE} without values removes the attribute; with values of a multi-valued
 *       attribute, one {@code remove} operation with a value filter is created per value,
 *       e.g. {@code members[value eq "123"]}.</li>
 * </ul>
 */
public final class ScimPatch {

    /** The schema URI of PATCH requests. */
    public static final String PATCH_OP_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    private ScimPatch() {
    }

    /**
     * Builds a PATCH request body.
     *
     * @param modifications the modifications
     * @param multiValued   tells whether an attribute is multi-valued
     * @return the request body
     */
    public static ObjectNode build(List<Modification> modifications, Predicate<String> multiValued) {
        ObjectNode body = Json.object();
        body.putArray("schemas").add(PATCH_OP_SCHEMA);
        ArrayNode ops = body.putArray("Operations");
        for (Modification m : modifications) {
            String path = m.attributeName();
            boolean multi = multiValued.test(path);
            switch (m.operation()) {
                case ADD -> {
                    if (!m.values().isEmpty()) {
                        op(ops, "add", path).set("value", value(m.values(), multi));
                    }
                }
                case REPLACE -> {
                    if (m.values().isEmpty()) {
                        op(ops, "remove", path);
                    } else {
                        op(ops, "replace", path).set("value", value(m.values(), multi));
                    }
                }
                case DELETE -> {
                    if (m.values().isEmpty() || !multi) {
                        op(ops, "remove", path);
                    } else {
                        for (Object v : m.values()) {
                            op(ops, "remove", path + "[value eq " + ScimFilterTranslator.quote(filterValue(v)) + "]");
                        }
                    }
                }
                default -> throw new IllegalStateException("unexpected operation " + m.operation());
            }
        }
        return body;
    }

    /**
     * Converts a value to JSON. JSON object strings become objects.
     *
     * @param value the value
     * @return the JSON node
     */
    public static JsonNode toNode(Object value) {
        if (value instanceof String s && s.startsWith("{") && s.endsWith("}")) {
            try {
                JsonNode node = Json.mapper().readTree(s);
                if (node.isObject()) {
                    return node;
                }
            } catch (JacksonException e) {
                // not JSON, send as string
            }
        }
        return Json.toNode(value);
    }

    private static JsonNode value(List<Object> values, boolean multi) {
        if (!multi && values.size() == 1) {
            return toNode(values.getFirst());
        }
        ArrayNode array = Json.mapper().createArrayNode();
        values.forEach(v -> array.add(toNode(v)));
        return array;
    }

    private static String filterValue(Object value) {
        JsonNode node = toNode(value);
        if (node.isObject()) {
            JsonNode v = node.path("value");
            return v.isMissingNode() ? node.toString() : v.asString();
        }
        return Attribute.asString(value);
    }

    private static ObjectNode op(ArrayNode ops, String op, String path) {
        ObjectNode node = ops.addObject();
        node.put("op", op);
        node.put("path", path);
        return node;
    }
}
