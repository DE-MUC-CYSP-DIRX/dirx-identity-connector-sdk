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

package com.example.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the client library of the target system.
 *
 * <p>TODO: Replace this class with the API of your target system, for example a REST
 * client, a JDBC data source or a vendor SDK. It keeps users in memory, so that the
 * template builds and its tests pass out of the box.
 */
final class ExampleClient implements AutoCloseable {

    /** Signals that the target system has no such user. */
    static final class NoSuchUserException extends Exception {
        private static final long serialVersionUID = 1L;

        NoSuchUserException(String id) {
            super("No such user: " + id);
        }
    }

    /** Signals that the target system already has the user. */
    static final class UserExistsException extends Exception {
        private static final long serialVersionUID = 1L;

        UserExistsException(String id) {
            super("User exists: " + id);
        }
    }

    private static final Map<String, Map<String, Map<String, List<Object>>>> SERVERS = new ConcurrentHashMap<>();

    private final Map<String, Map<String, List<Object>>> users;

    ExampleClient(String url) {
        users = SERVERS.computeIfAbsent(url, k -> new ConcurrentHashMap<>());
    }

    void ping() {
        // A real client would send a cheap request here, e.g. GET /health.
    }

    void createUser(String id, Map<String, List<Object>> attributes) throws UserExistsException {
        if (users.putIfAbsent(key(id), copy(id, attributes)) != null) {
            throw new UserExistsException(id);
        }
    }

    Map<String, List<Object>> getUser(String id) throws NoSuchUserException {
        Map<String, List<Object>> user = users.get(key(id));
        if (user == null) {
            throw new NoSuchUserException(id);
        }
        return copy(id, user);
    }

    void putUser(String id, Map<String, List<Object>> attributes) throws NoSuchUserException {
        if (users.replace(key(id), copy(id, attributes)) == null) {
            throw new NoSuchUserException(id);
        }
    }

    void deleteUser(String id) throws NoSuchUserException {
        if (users.remove(key(id)) == null) {
            throw new NoSuchUserException(id);
        }
    }

    /** Lists user identifiers in a stable order, starting at {@code offset}. */
    List<String> listUsers(int offset, int limit) {
        List<String> ids = users.values().stream()
                .map(u -> (String) u.get(ExampleConnector.ID).get(0))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        int from = Math.min(offset, ids.size());
        return new ArrayList<>(ids.subList(from, Math.min(ids.size(), from + limit)));
    }

    @Override
    public void close() {
        // A real client would release connections here.
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<Object>> copy(String id, Map<String, List<Object>> attributes) {
        Map<String, List<Object>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        attributes.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        copy.put(ExampleConnector.ID, List.of(id));
        return copy;
    }
}
