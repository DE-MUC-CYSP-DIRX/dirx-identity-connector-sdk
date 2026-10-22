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

package solutions.dirx.identity.connector.sdk.samples.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateOp;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteOp;
import solutions.dirx.identity.connector.sdk.api.DeleteRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.ResultHandler;
import solutions.dirx.identity.connector.sdk.api.SchemaOp;
import solutions.dirx.identity.connector.sdk.api.SearchOp;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.SearchResult;
import solutions.dirx.identity.connector.sdk.api.TestOp;
import solutions.dirx.identity.connector.sdk.api.UpdateOp;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.FilterEvaluator;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.Schema;
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;

/**
 * Connector that stores objects of one type as rows of a CSV file. The first row holds the
 * column names; one column holds the identifier. Multiple values of an attribute are joined with
 * a value separator.
 *
 * <p>Configuration properties:
 * <ul>
 * <li>{@value #PROP_FILE} (required): path of the CSV file</li>
 * <li>{@value #PROP_OBJECT_TYPE}: object type name, default {@code Account}</li>
 * <li>{@value #PROP_ID_COLUMN}: identifier column, default {@code id}</li>
 * <li>{@value #PROP_SEPARATOR}: field separator, default {@code ,}</li>
 * <li>{@value #PROP_VALUE_SEPARATOR}: separator of multiple values, default {@code |}</li>
 * <li>{@value #PROP_COLUMNS}: columns used to create the file if it does not exist</li>
 * </ul>
 *
 * <p>Every operation reads the whole file, and every write replaces it atomically. This keeps
 * the sample simple; it is not meant for large files.
 */
@ConnectorInfo(name = "sample-csv", displayName = "CSV file (sample)",
        description = "Sample connector that manages objects in a CSV file")
public class CsvConnector implements Connector, TestOp, SchemaOp, CreateOp, SearchOp, UpdateOp, DeleteOp {

    /** Property: path of the CSV file. */
    public static final String PROP_FILE = "csv.file";
    /** Property: object type name. */
    public static final String PROP_OBJECT_TYPE = "csv.objectType";
    /** Property: identifier column. */
    public static final String PROP_ID_COLUMN = "csv.identifierColumn";
    /** Property: field separator. */
    public static final String PROP_SEPARATOR = "csv.separator";
    /** Property: separator of multiple values within one field. */
    public static final String PROP_VALUE_SEPARATOR = "csv.valueSeparator";
    /** Property: columns of a new file (multi-valued). */
    public static final String PROP_COLUMNS = "csv.columns";

    private static final Map<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private Path file;
    private String objectType;
    private String idColumn;
    private String valueSeparator;
    private CsvFormat format;
    private ReentrantLock lock;

    /** Creates an uninitialized connector. */
    public CsvConnector() {
        // initialized by init()
    }

    @Override
    public void init(ConnectorContext context) throws ConnectorException {
        ConnectorConfiguration config = context.configuration();
        file = Path.of(config.require(PROP_FILE)).toAbsolutePath().normalize();
        objectType = config.get(PROP_OBJECT_TYPE).orElse("Account");
        idColumn = config.get(PROP_ID_COLUMN).orElse("id");
        valueSeparator = config.get(PROP_VALUE_SEPARATOR).orElse("|");
        String separator = config.get(PROP_SEPARATOR).orElse(",");
        if (separator.length() != 1 || valueSeparator.isEmpty()) {
            throw new ConnectorException(ErrorCode.CONFIGURATION,
                    PROP_SEPARATOR + " must be one character and " + PROP_VALUE_SEPARATOR + " must not be empty");
        }
        try {
            format = new CsvFormat(separator.charAt(0));
        } catch (IllegalArgumentException e) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "invalid " + PROP_SEPARATOR, e);
        }
        lock = LOCKS.computeIfAbsent(file, p -> new ReentrantLock());
        List<String> columns = config.getList(PROP_COLUMNS);
        if (!Files.exists(file)) {
            if (columns.isEmpty()) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        file + " does not exist and " + PROP_COLUMNS + " is not set");
            }
            if (columns.stream().noneMatch(idColumn::equalsIgnoreCase)) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        PROP_COLUMNS + " does not contain the identifier column " + idColumn);
            }
            withLock(() -> {
                if (!Files.exists(file)) {
                    write(new Table(columns, new ArrayList<>()));
                }
                return null;
            });
        }
    }

    @Override
    public void close() {
        // nothing to release; the file is opened per operation
    }

    @Override
    public void test() throws ConnectorException {
        withLock(this::read);
    }

    @Override
    public Schema schema() throws ConnectorException {
        Table table = withLock(this::read);
        List<Schema.AttributeInfo> attributes = new ArrayList<>();
        for (String column : table.columns()) {
            Schema.AttributeInfo info = Schema.AttributeInfo.of(column, Schema.AttributeType.STRING);
            attributes.add(column.equalsIgnoreCase(idColumn) ? info.asRequired() : info.asMultiValued());
        }
        return Schema.of(Schema.ObjectClassInfo.of(objectType, attributes.toArray(Schema.AttributeInfo[]::new)));
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        checkType(request.objectType());
        return withLock(() -> {
            Table table = read();
            String[] row = new String[table.columns().size()];
            Arrays.fill(row, "");
            for (Attribute attribute : request.attributes()) {
                row[table.column(attribute.name())] = join(attribute.values());
            }
            int idIndex = table.column(idColumn);
            if (request.identifier() != null) {
                row[idIndex] = request.identifier().value();
            }
            if (row[idIndex].isEmpty()) {
                throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION, "identifier is missing");
            }
            if (table.find(row[idIndex]) >= 0) {
                throw new ConnectorException(ErrorCode.ALREADY_EXISTS, row[idIndex] + " already exists");
            }
            table.rows().add(Arrays.asList(row));
            write(table);
            return CreateResult.of(Identifier.of(row[idIndex]));
        });
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        checkType(request.objectType());
        Table table = withLock(this::read);
        List<ConnectorObject> matches = new ArrayList<>();
        for (List<String> row : table.rows()) {
            ConnectorObject object = toObject(table, row, request);
            boolean inBase = request.base() == null || request.scope() != SearchScope.OBJECT
                    || object.identifier().value().equalsIgnoreCase(request.base().value());
            if (inBase && (request.filter() == null || FilterEvaluator.matches(request.filter(), object))) {
                matches.add(object);
            }
        }
        int offset = parseCookie(request.pageCookie());
        int end = request.pageSize() > 0 ? Math.min(matches.size(), offset + request.pageSize()) : matches.size();
        for (int i = offset; i < end; i++) {
            if (!handler.handle(matches.get(i))) {
                return SearchResult.done();
            }
        }
        return end < matches.size() ? SearchResult.nextPage(Integer.toString(end)) : SearchResult.done();
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        checkType(request.objectType());
        return withLock(() -> {
            Table table = read();
            int rowIndex = table.find(request.identifier().value());
            if (rowIndex < 0) {
                throw new ConnectorException(ErrorCode.NOT_FOUND, request.identifier() + " not found");
            }
            List<String> row = new ArrayList<>(table.rows().get(rowIndex));
            int idIndex = table.column(idColumn);
            for (Modification mod : request.modifications()) {
                int col = table.column(mod.attributeName());
                List<String> current = split(row.get(col));
                List<String> values = mod.values().stream().map(Attribute::asString).toList();
                List<String> updated = switch (mod.operation()) {
                    case REPLACE -> values;
                    case ADD -> {
                        List<String> result = new ArrayList<>(current);
                        values.stream().filter(v -> !result.contains(v)).forEach(result::add);
                        yield result;
                    }
                    case DELETE -> values.isEmpty() ? List.of()
                            : current.stream().filter(v -> !values.contains(v)).toList();
                };
                if (col == idIndex && updated.size() != 1) {
                    throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION, idColumn + " requires exactly one value");
                }
                row.set(col, join(updated));
            }
            String oldId = table.rows().get(rowIndex).get(idIndex);
            String newId = row.get(idIndex);
            boolean renamed = !newId.equals(oldId);
            if (renamed && !newId.equalsIgnoreCase(oldId) && table.find(newId) >= 0) {
                throw new ConnectorException(ErrorCode.ALREADY_EXISTS, newId + " already exists");
            }
            table.rows().set(rowIndex, row);
            write(table);
            return renamed ? UpdateResult.renamed(Identifier.of(newId)) : UpdateResult.unchanged();
        });
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        checkType(request.objectType());
        withLock(() -> {
            Table table = read();
            int rowIndex = table.find(request.identifier().value());
            if (rowIndex < 0) {
                throw new ConnectorException(ErrorCode.NOT_FOUND, request.identifier() + " not found");
            }
            table.rows().remove(rowIndex);
            write(table);
            return null;
        });
    }

    private ConnectorObject toObject(Table table, List<String> row, SearchRequest request) {
        String id = row.get(table.column0(idColumn));
        ConnectorObject.Builder builder = ConnectorObject.builder(objectType, Identifier.of(id));
        for (int i = 0; i < table.columns().size(); i++) {
            String name = table.columns().get(i);
            List<String> values = split(row.get(i));
            if (!values.isEmpty() && request.isRequested(name)) {
                builder.attribute(name, values);
            }
        }
        return builder.build();
    }

    private void checkType(String type) throws ConnectorException {
        if (!objectType.equalsIgnoreCase(type)) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "unsupported object type " + type);
        }
    }

    private String join(List<?> values) throws ConnectorException {
        List<String> strings = new ArrayList<>();
        for (Object value : values) {
            String s = Attribute.asString(value);
            if (s.contains(valueSeparator)) {
                throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION,
                        "value must not contain the value separator " + valueSeparator);
            }
            if (!s.isEmpty()) {
                strings.add(s);
            }
        }
        return String.join(valueSeparator, strings);
    }

    private List<String> split(String field) {
        if (field.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int start = 0;
        int pos;
        while ((pos = field.indexOf(valueSeparator, start)) >= 0) {
            values.add(field.substring(start, pos));
            start = pos + valueSeparator.length();
        }
        values.add(field.substring(start));
        return values;
    }

    private static int parseCookie(String cookie) throws ConnectorException {
        if (cookie == null || cookie.isEmpty()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(cookie);
            if (offset < 0) {
                throw new NumberFormatException(cookie);
            }
            return offset;
        } catch (NumberFormatException e) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "invalid page cookie " + cookie, e);
        }
    }

    private Table read() throws ConnectorException {
        List<List<String>> records;
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            records = format.read(in);
        } catch (IOException e) {
            throw new ConnectorException(ErrorCode.CONNECTION_FAILED, "cannot read " + file + ": " + e.getMessage(), e);
        }
        if (records.isEmpty()) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, file + " has no header row");
        }
        List<String> columns = records.getFirst();
        if (columns.stream().noneMatch(idColumn::equalsIgnoreCase)) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, file + " has no column " + idColumn);
        }
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> row = new ArrayList<>(records.get(i));
            while (row.size() < columns.size()) {
                row.add("");
            }
            rows.add(row.subList(0, columns.size()));
        }
        return new Table(columns, rows);
    }

    private void write(Table table) throws ConnectorException {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            try {
                try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    format.write(out, table.columns());
                    for (List<String> row : table.rows()) {
                        format.write(out, row);
                    }
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new ConnectorException(ErrorCode.OTHER, "cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    private <T> T withLock(Action<T> action) throws ConnectorException {
        lock.lock();
        try {
            return action.run();
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface Action<T> {
        T run() throws ConnectorException;
    }

    private final class Table {
        private final List<String> columns;
        private final List<List<String>> rows;
        private final Map<String, Integer> index = new LinkedHashMap<>();

        Table(List<String> columns, List<List<String>> rows) {
            this.columns = List.copyOf(columns);
            this.rows = rows;
            for (int i = 0; i < columns.size(); i++) {
                index.putIfAbsent(columns.get(i).toLowerCase(Locale.ROOT), i);
            }
        }

        List<String> columns() {
            return columns;
        }

        List<List<String>> rows() {
            return rows;
        }

        int column(String name) throws ConnectorException {
            Integer i = index.get(name.toLowerCase(Locale.ROOT));
            if (i == null) {
                throw new ConnectorException(ErrorCode.NO_SUCH_ATTRIBUTE, "unknown attribute " + name);
            }
            return i;
        }

        int column0(String name) {
            return Optional.ofNullable(index.get(name.toLowerCase(Locale.ROOT))).orElse(0);
        }

        int find(String id) {
            int col = column0(idColumn);
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).get(col).equalsIgnoreCase(id)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
