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

package solutions.dirx.identity.connector.sdk.rest;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversion between JSON documents and SDK attributes.
 *
 * <p>JSON objects are flattened: nested object properties become attributes whose names are
 * joined with {@code '.'} (e.g. {@code name.givenName}). Arrays of scalars become multi-valued
 * attributes; arrays containing objects are kept as one JSON string value per element.
 * Numbers that fit into {@code Integer}/{@code Long} are converted to these types, other
 * numbers to their string form. {@code null} values and empty arrays are omitted.
 */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    /**
     * Returns the shared JSON mapper.
     *
     * @return the mapper
     */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    /**
     * Creates an empty JSON object.
     *
     * @return the object
     */
    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    /**
     * Flattens a JSON object into attributes.
     *
     * @param node    the object
     * @param exclude top-level property names to skip (case-sensitive), e.g. the id property
     * @return the attributes in document order
     */
    public static List<Attribute> toAttributes(JsonNode node, String... exclude) {
        Map<String, List<Object>> values = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            List<String> excluded = List.of(exclude);
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (!excluded.contains(e.getKey())) {
                    flatten(e.getKey(), e.getValue(), values);
                }
            }
        }
        List<Attribute> result = new ArrayList<>();
        values.forEach((name, v) -> {
            if (!v.isEmpty()) {
                result.add(new Attribute(name, v));
            }
        });
        return result;
    }

    private static void flatten(String name, JsonNode value, Map<String, List<Object>> out) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return;
        }
        if (value.isObject()) {
            for (Map.Entry<String, JsonNode> e : value.properties()) {
                flatten(name + "." + e.getKey(), e.getValue(), out);
            }
        } else if (value.isArray()) {
            List<Object> list = out.computeIfAbsent(name, k -> new ArrayList<>());
            for (JsonNode element : value.values()) {
                Object v = element.isContainer() ? MAPPER.writeValueAsString(element) : toValue(element);
                if (v != null) {
                    list.add(v);
                }
            }
        } else {
            Object v = toValue(value);
            if (v != null) {
                out.computeIfAbsent(name, k -> new ArrayList<>()).add(v);
            }
        }
    }

    /**
     * Converts a scalar JSON node into an attribute value.
     *
     * @param node the node
     * @return the value, or {@code null} for JSON {@code null}
     */
    public static Object toValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.intValue();
        }
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return node.longValue();
        }
        if (node.isContainer()) {
            return MAPPER.writeValueAsString(node);
        }
        return node.asString();
    }

    /**
     * Converts an attribute value into a JSON node. Secrets are revealed; binary values are
     * Base64 encoded.
     *
     * @param value the value
     * @return the node
     */
    public static JsonNode toNode(Object value) {
        JsonNodeFactory f = MAPPER.getNodeFactory();
        return switch (value) {
            case null -> f.nullNode();
            case String s -> f.stringNode(s);
            case Boolean b -> f.booleanNode(b);
            case Integer i -> f.numberNode(i);
            case Long l -> f.numberNode(l);
            case byte[] bytes -> f.stringNode(Base64.getEncoder().encodeToString(bytes));
            case SecretValue s -> f.stringNode(s.reveal());
            case JsonNode n -> n;
            default -> f.stringNode(String.valueOf(value));
        };
    }

    /**
     * Builds a JSON object from attributes, expanding dotted names into nested objects.
     *
     * @param attributes  the attributes
     * @param multiValued tells whether an attribute (by name) is written as array even if it has
     *                    a single value; attributes with several values are always arrays
     * @return the object
     */
    public static ObjectNode toObject(Collection<Attribute> attributes, java.util.function.Predicate<String> multiValued) {
        ObjectNode root = object();
        for (Attribute a : attributes) {
            set(root, a.name(), a.values(), multiValued.test(a.name()));
        }
        return root;
    }

    /**
     * Builds a JSON object from attributes; only attributes with several values become arrays.
     *
     * @param attributes the attributes
     * @return the object
     */
    public static ObjectNode toObject(Collection<Attribute> attributes) {
        return toObject(attributes, n -> false);
    }

    /**
     * Applies modifications to a JSON object (e.g. for read-modify-write APIs). Dotted names
     * address nested properties.
     *
     * @param target        the object to modify
     * @param modifications the modifications
     * @param multiValued   tells whether an attribute is written as array
     */
    public static void apply(ObjectNode target, List<Modification> modifications,
                             java.util.function.Predicate<String> multiValued) {
        for (Modification m : modifications) {
            switch (m.operation()) {
                case REPLACE -> set(target, m.attributeName(), m.values(), multiValued.test(m.attributeName()));
                case ADD -> {
                    List<Object> merged = new ArrayList<>(current(target, m.attributeName()));
                    for (Object v : m.values()) {
                        if (!containsValue(merged, v)) {
                            merged.add(v);
                        }
                    }
                    set(target, m.attributeName(), merged, true);
                }
                case DELETE -> {
                    if (m.values().isEmpty()) {
                        set(target, m.attributeName(), List.of(), false);
                    } else {
                        List<Object> remaining = new ArrayList<>(current(target, m.attributeName()));
                        remaining.removeIf(v -> containsValue(m.values(), v));
                        set(target, m.attributeName(), remaining, multiValued.test(m.attributeName()));
                    }
                }
            }
        }
    }

    private static boolean containsValue(List<Object> list, Object v) {
        String s = Attribute.asString(v);
        return list.stream().anyMatch(x -> Objects.equals(Attribute.asString(x), s));
    }

    private static List<Object> current(ObjectNode root, String dotted) {
        JsonNode node = root;
        for (String part : dotted.split("\\.")) {
            node = node.path(part);
        }
        Map<String, List<Object>> values = new LinkedHashMap<>();
        flatten("v", node, values);
        return values.getOrDefault("v", List.of());
    }

    private static void set(ObjectNode root, String dotted, List<Object> values, boolean asArray) {
        String[] parts = dotted.split("\\.");
        ObjectNode parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode child = parent.get(parts[i]);
            if (child == null || !child.isObject()) {
                if (values.isEmpty()) {
                    return;
                }
                child = parent.putObject(parts[i]);
            }
            parent = (ObjectNode) child;
        }
        String leaf = parts[parts.length - 1];
        if (values.isEmpty()) {
            parent.remove(leaf);
        } else if (values.size() == 1 && !asArray) {
            parent.set(leaf, toNode(values.getFirst()));
        } else {
            ArrayNode array = parent.putArray(leaf);
            values.forEach(v -> array.add(toNode(v)));
        }
    }
}
