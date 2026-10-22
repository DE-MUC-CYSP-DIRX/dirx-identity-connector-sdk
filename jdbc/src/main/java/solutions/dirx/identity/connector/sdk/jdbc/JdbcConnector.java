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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
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
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.model.Schema;
import solutions.dirx.identity.connector.sdk.api.model.Schema.AttributeInfo;
import solutions.dirx.identity.connector.sdk.api.model.Schema.AttributeType;
import solutions.dirx.identity.connector.sdk.api.model.Schema.ObjectClassInfo;
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;

/**
 * A generic connector for relational databases that maps each object type to one table. It can
 * be used as is or subclassed, e.g. to obtain connections from a pool or to support multi-valued
 * attributes stored in additional tables.
 *
 * <p>Each row is an object; its identifier is the value of the key column and every other column
 * is a single-valued attribute named like the column (names are matched ignoring case). Filters are
 * translated to parameterized SQL with {@link SqlFilterTranslator}, and paging uses
 * {@code OFFSET ... ROWS FETCH NEXT ... ROWS ONLY} (SQL:2008) ordered by the key column.
 *
 * <p>Modifications: {@code REPLACE} and {@code ADD} set the column, {@code DELETE} and
 * {@code REPLACE} without values set it to {@code NULL}. Replacing the key column renames the
 * object.
 *
 * <p>Configuration (besides the connection URL, which is the JDBC URL, user and password):
 * <table>
 * <caption>Configuration properties</caption>
 * <tr><th>Property</th><th>Default</th><th>Meaning</th></tr>
 * <tr><td>{@value #PROP_TABLES}</td><td>(required)</td>
 *     <td>multi-valued {@code objectType=table} mappings; tables may be schema qualified</td></tr>
 * <tr><td>{@value #PROP_KEY_COLUMNS}</td><td>primary key</td>
 *     <td>multi-valued {@code objectType=column} mappings for tables without single-column primary key</td></tr>
 * <tr><td>{@value #PROP_DRIVER}</td><td>(none)</td>
 *     <td>JDBC driver class to load, only needed for drivers that do not register themselves</td></tr>
 * <tr><td>{@value #PROP_QUERY_TIMEOUT}</td><td>0</td><td>statement timeout in seconds, 0 for none</td></tr>
 * <tr><td>{@value #PROP_TEST_QUERY}</td><td>(none)</td>
 *     <td>SQL query run by the test operation; without it, {@link Connection#isValid} is used</td></tr>
 * <tr><td>{@value #PROP_PAGING}</td><td>{@code offset-fetch}</td>
 *     <td>{@code offset-fetch} for SQL:2008 paging or {@code limit-offset} (MySQL, MariaDB, SQLite)</td></tr>
 * </table>
 * Further properties starting with {@value #PROP_DRIVER_PROPERTY_PREFIX} are passed to the driver.
 */
@ConnectorInfo(name = "jdbc", displayName = "Database (JDBC)",
        description = "Generic table-based connector for relational databases")
public class JdbcConnector implements Connector, TestOp, SchemaOp, CreateOp, SearchOp, UpdateOp, DeleteOp {

    /** Property: object type to table mappings. */
    public static final String PROP_TABLES = "jdbc.tables";
    /** Property: object type to key column mappings. */
    public static final String PROP_KEY_COLUMNS = "jdbc.keyColumns";
    /** Property: driver class name. */
    public static final String PROP_DRIVER = "jdbc.driver";
    /** Property: statement timeout in seconds. */
    public static final String PROP_QUERY_TIMEOUT = "jdbc.queryTimeout";
    /** Property: SQL query of the test operation. */
    public static final String PROP_TEST_QUERY = "jdbc.testQuery";
    /** Property: paging syntax. */
    public static final String PROP_PAGING = "jdbc.paging";
    /** Prefix of properties passed to the JDBC driver. */
    public static final String PROP_DRIVER_PROPERTY_PREFIX = "jdbc.property.";

    private ConnectorConfiguration configuration;
    private Connection connection;
    private final Map<String, String> tableNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> keyColumns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, JdbcTable> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private int queryTimeout;
    private boolean limitOffset;

    /** Creates the connector. */
    public JdbcConnector() {
    }

    @Override
    public void init(ConnectorContext context) throws ConnectorException {
        configuration = context.configuration();
        tableNames.putAll(mappings(PROP_TABLES));
        keyColumns.putAll(mappings(PROP_KEY_COLUMNS));
        if (tableNames.isEmpty()) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, PROP_TABLES + " is required");
        }
        queryTimeout = configuration.getInt(PROP_QUERY_TIMEOUT, 0);
        String paging = configuration.get(PROP_PAGING).orElse("offset-fetch");
        switch (paging) {
            case "offset-fetch" -> limitOffset = false;
            case "limit-offset" -> limitOffset = true;
            default -> throw new ConnectorException(ErrorCode.CONFIGURATION, "unsupported " + PROP_PAGING + ": " + paging);
        }
        String driver = configuration.get(PROP_DRIVER).orElse(null);
        if (driver != null) {
            try {
                Class.forName(driver, true, getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ConnectorException(ErrorCode.CONFIGURATION, "JDBC driver " + driver + " not found", e);
            }
        }
    }

    @Override
    public void close() {
        closeConnection();
        tables.clear();
    }

    /** {@return the configuration passed to {@link #init}} */
    protected ConnectorConfiguration configuration() {
        return configuration;
    }

    /**
     * Opens a new database connection. The default implementation uses {@link DriverManager} with
     * the connection URL, user, password and the driver properties of the configuration. Override it
     * to use a {@code DataSource}.
     *
     * @return the connection
     * @throws SQLException       if connecting fails
     * @throws ConnectorException if the configuration is incomplete
     */
    protected Connection openConnection() throws SQLException, ConnectorException {
        ConnectionSettings settings = configuration.connection();
        String url = settings.urlValue().orElseThrow(
                () -> new ConnectorException(ErrorCode.CONFIGURATION, "the JDBC URL is required"));
        Properties props = new Properties();
        for (String name : configuration.names()) {
            if (name.startsWith(PROP_DRIVER_PROPERTY_PREFIX)) {
                configuration.get(name).ifPresent(v -> props.setProperty(name.substring(PROP_DRIVER_PROPERTY_PREFIX.length()), v));
            }
        }
        settings.userValue().ifPresent(u -> props.setProperty("user", u));
        settings.passwordValue().ifPresent(p -> props.setProperty("password", p.reveal()));
        return DriverManager.getConnection(url, props);
    }

    /**
     * Returns the open connection, connecting if necessary.
     *
     * @return the connection
     * @throws ConnectorException if connecting fails
     */
    protected Connection connection() throws ConnectorException {
        try {
            if (connection == null || connection.isClosed()) {
                connection = openConnection();
                connection.setAutoCommit(true);
            }
            return connection;
        } catch (SQLException e) {
            closeConnection();
            throw SqlErrorMapper.map("connect", e);
        }
    }

    /**
     * Returns the description of the table of an object type.
     *
     * @param objectType the object type
     * @return the table description
     * @throws ConnectorException {@link ErrorCode#INVALID_REQUEST} for unknown object types, or if
     *                            reading the metadata fails
     */
    protected JdbcTable table(String objectType) throws ConnectorException {
        JdbcTable table = tables.get(objectType);
        if (table == null) {
            String name = tableNames.get(objectType);
            if (name == null) {
                throw new ConnectorException(ErrorCode.INVALID_REQUEST, "unsupported object type " + objectType);
            }
            try {
                table = JdbcTable.load(connection(), objectType, name, keyColumns.get(objectType));
            } catch (SQLException e) {
                throw failure("read metadata of " + name, e);
            }
            tables.put(objectType, table);
        }
        return table;
    }

    @Override
    public void test() throws ConnectorException {
        String query = configuration.get(PROP_TEST_QUERY).orElse(null);
        try {
            if (query == null) {
                if (!connection().isValid(Math.max(queryTimeout, 10))) {
                    closeConnection();
                    throw new ConnectorException(ErrorCode.CONNECTION_FAILED, "the database connection is not valid");
                }
            } else {
                try (Statement st = connection().createStatement()) {
                    st.setQueryTimeout(queryTimeout);
                    st.execute(query);
                }
            }
            for (String objectType : tableNames.keySet()) {
                table(objectType);
            }
        } catch (SQLException e) {
            throw failure("test", e);
        }
    }

    @Override
    public Schema schema() throws ConnectorException {
        List<ObjectClassInfo> classes = new ArrayList<>();
        for (String objectType : tableNames.keySet()) {
            JdbcTable table = table(objectType);
            List<AttributeInfo> attributes = new ArrayList<>();
            for (JdbcColumn column : table.columns().values()) {
                AttributeInfo info = AttributeInfo.of(column.attributeName(), attributeType(column));
                if (column.generated()) {
                    info = info.asReadOnly();
                } else if (column.equals(table.keyColumn()) || (!column.nullable() && !column.hasDefault())) {
                    info = info.asRequired();
                }
                attributes.add(info);
            }
            classes.add(new ObjectClassInfo(objectType, attributes));
        }
        return new Schema(classes);
    }

    /**
     * Returns the attribute type of a column.
     *
     * @param column the column
     * @return the attribute type
     */
    protected AttributeType attributeType(JdbcColumn column) {
        if (column.isBoolean()) {
            return AttributeType.BOOLEAN;
        }
        if (column.isBinary()) {
            return AttributeType.BINARY;
        }
        return switch (column.sqlType()) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> AttributeType.INTEGER;
            case Types.BIGINT -> AttributeType.LONG;
            default -> AttributeType.STRING;
        };
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        JdbcTable table = table(request.objectType());
        Map<JdbcColumn, Object> values = new LinkedHashMap<>();
        values.put(table.keyColumn(), JdbcValues.toParameter(request.identifier().value(), table.keyColumn()));
        for (Attribute attribute : request.attributes()) {
            JdbcColumn column = table.column(attribute.name());
            if (column.equals(table.keyColumn())) {
                continue;
            }
            Object value = JdbcValues.toParameter(single(attribute.name(), attribute.values()), column);
            if (value != null) {
                values.put(column, value);
            }
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table.quotedName()).append(" (");
        StringBuilder marks = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<JdbcColumn, Object> e : values.entrySet()) {
            if (!params.isEmpty()) {
                sql.append(", ");
                marks.append(", ");
            }
            sql.append(e.getKey().quotedName());
            marks.append('?');
            params.add(e.getValue());
        }
        sql.append(") VALUES (").append(marks).append(')');
        execute("create " + request.identifier().value(), new SqlFragment(sql.toString(), params));
        return CreateResult.of(request.identifier());
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        JdbcTable table = table(request.objectType());
        List<JdbcColumn> selected = new ArrayList<>();
        selected.add(table.keyColumn());
        for (JdbcColumn column : table.columns().values()) {
            if (!column.equals(table.keyColumn()) && request.isRequested(column.attributeName())) {
                selected.add(column);
            }
        }
        List<SqlFragment> conditions = new ArrayList<>();
        if (request.base() != null && request.scope() == SearchScope.OBJECT) {
            conditions.add(SqlFragment.of(table.keyColumn().quotedName() + " = ?",
                    JdbcValues.toParameter(request.base().value(), table.keyColumn())));
        }
        if (request.filter() != null) {
            conditions.add(new SqlFilterTranslator(table).translate(request.filter()));
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < selected.size(); i++) {
            sql.append(i == 0 ? "" : ", ").append(selected.get(i).quotedName());
        }
        sql.append(" FROM ").append(table.quotedName());
        List<Object> params = new ArrayList<>();
        if (!conditions.isEmpty()) {
            SqlFragment where = SqlFragment.join(" AND ", conditions);
            sql.append(" WHERE ").append(where.sql());
            params.addAll(where.parameters());
        }
        sql.append(" ORDER BY ").append(table.keyColumn().quotedName());
        int pageSize = request.pageSize();
        long offset = offset(request.pageCookie());
        if (pageSize > 0) {
            sql.append(' ').append(pagingClause(offset, pageSize + 1L));
        }
        String context = "search " + request.objectType();
        try (PreparedStatement ps = prepare(sql.toString(), params)) {
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    if (pageSize > 0 && count == pageSize) {
                        return SearchResult.nextPage(Long.toString(offset + pageSize));
                    }
                    count++;
                    if (!handler.handle(toObject(request, selected, rs))) {
                        return SearchResult.done();
                    }
                }
            }
        } catch (SQLException e) {
            throw failure(context, e);
        }
        return SearchResult.done();
    }

    /**
     * Returns the paging clause appended to search statements.
     *
     * @param offset the number of rows to skip
     * @param limit  the maximum number of rows to return
     * @return the SQL clause
     */
    protected String pagingClause(long offset, long limit) {
        return limitOffset ? "LIMIT " + limit + " OFFSET " + offset
                : "OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    /**
     * Converts the current row to an object.
     *
     * @param request the search request
     * @param columns the selected columns; the first one is the key column
     * @param rs      the result set positioned on the row
     * @return the object
     * @throws SQLException if reading fails
     */
    protected ConnectorObject toObject(SearchRequest request, List<JdbcColumn> columns, ResultSet rs)
            throws SQLException {
        Object key = JdbcValues.read(rs, 1);
        if (key == null) {
            throw new SQLException("key column " + columns.getFirst().columnName() + " is null");
        }
        Identifier id = Identifier.of(Attribute.asString(key));
        ConnectorObject.Builder builder = ConnectorObject.builder(request.objectType(), id);
        for (int i = 0; i < columns.size(); i++) {
            JdbcColumn column = columns.get(i);
            if (i == 0 && !request.isRequested(column.attributeName())) {
                continue;
            }
            Object value = JdbcValues.read(rs, i + 1);
            if (value != null) {
                builder.attribute(column.attributeName(), value);
            }
        }
        return builder.build();
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        JdbcTable table = table(request.objectType());
        Map<JdbcColumn, Object> values = new LinkedHashMap<>();
        for (Modification m : request.modifications()) {
            JdbcColumn column = table.column(m.attributeName());
            Object value = switch (m.operation()) {
                case ADD, REPLACE -> m.values().isEmpty() ? null
                        : JdbcValues.toParameter(single(m.attributeName(), m.values()), column);
                case DELETE -> null;
            };
            if (value == null && column.equals(table.keyColumn())) {
                throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION, "the key column cannot be removed");
            }
            values.put(column, value);
        }
        Object key = JdbcValues.toParameter(request.identifier().value(), table.keyColumn());
        String context = "update " + request.identifier().value();
        if (values.isEmpty()) {
            SqlFragment exists = SqlFragment.of("SELECT 1 FROM " + table.quotedName() + " WHERE "
                    + table.keyColumn().quotedName() + " = ?", key);
            try (PreparedStatement ps = prepare(exists.sql(), exists.parameters()); ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw notFound(request.identifier());
                }
            } catch (SQLException e) {
                throw failure(context, e);
            }
            return UpdateResult.unchanged();
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(table.quotedName()).append(" SET ");
        List<Object> params = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<JdbcColumn, Object> e : values.entrySet()) {
            sql.append(first ? "" : ", ").append(e.getKey().quotedName());
            first = false;
            if (e.getValue() == null) {
                sql.append(" = NULL");
            } else {
                sql.append(" = ?");
                params.add(e.getValue());
            }
        }
        sql.append(" WHERE ").append(table.keyColumn().quotedName()).append(" = ?");
        params.add(key);
        if (execute(context, new SqlFragment(sql.toString(), params)) == 0) {
            throw notFound(request.identifier());
        }
        Object newKey = values.get(table.keyColumn());
        if (newKey != null && !Attribute.asString(newKey).equals(request.identifier().value())) {
            return UpdateResult.renamed(Identifier.of(Attribute.asString(newKey)));
        }
        return UpdateResult.unchanged();
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        JdbcTable table = table(request.objectType());
        SqlFragment sql = SqlFragment.of("DELETE FROM " + table.quotedName() + " WHERE "
                + table.keyColumn().quotedName() + " = ?",
                JdbcValues.toParameter(request.identifier().value(), table.keyColumn()));
        if (execute("delete " + request.identifier().value(), sql) == 0) {
            throw notFound(request.identifier());
        }
    }

    /**
     * Executes an update statement.
     *
     * @param context a description of the operation, used in error messages
     * @param sql     the statement
     * @return the update count
     * @throws ConnectorException if execution fails
     */
    protected int execute(String context, SqlFragment sql) throws ConnectorException {
        try (PreparedStatement ps = prepare(sql.sql(), sql.parameters())) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw failure(context, e);
        }
    }

    /**
     * Prepares a statement and binds its parameters.
     *
     * @param sql    the SQL
     * @param params the parameters
     * @return the statement
     * @throws SQLException       if preparing fails
     * @throws ConnectorException if connecting fails
     */
    protected PreparedStatement prepare(String sql, List<Object> params) throws SQLException, ConnectorException {
        PreparedStatement ps = connection().prepareStatement(sql);
        try {
            ps.setQueryTimeout(queryTimeout);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }

    /**
     * Maps an exception and closes the connection if it is broken.
     *
     * @param context a description of the operation
     * @param e       the exception
     * @return the connector exception
     */
    protected ConnectorException failure(String context, SQLException e) {
        if (SqlErrorMapper.isConnectionFailure(e)) {
            closeConnection();
        }
        return SqlErrorMapper.map(context, e);
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // ignore, the connection is discarded anyway
            }
            connection = null;
        }
    }

    private Map<String, String> mappings(String property) throws ConnectorException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : configuration.getList(property)) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        property + " entries must have the form objectType=name: " + entry);
            }
            result.put(entry.substring(0, eq).strip(), entry.substring(eq + 1).strip());
        }
        return result;
    }

    private static Object single(String name, List<Object> values) throws ConnectorException {
        if (values.size() > 1) {
            throw new ConnectorException(ErrorCode.SCHEMA_VIOLATION, "attribute " + name + " is single-valued");
        }
        return values.isEmpty() ? null : values.getFirst();
    }

    private static long offset(String cookie) throws ConnectorException {
        if (cookie == null || cookie.isEmpty()) {
            return 0;
        }
        try {
            long offset = Long.parseLong(cookie);
            if (offset < 0) {
                throw new NumberFormatException();
            }
            return offset;
        } catch (NumberFormatException e) {
            throw new ConnectorException(ErrorCode.INVALID_REQUEST, "invalid page cookie " + cookie);
        }
    }

    private static ConnectorException notFound(Identifier identifier) {
        return new ConnectorException(ErrorCode.NOT_FOUND, "object " + identifier.value() + " not found");
    }
}
