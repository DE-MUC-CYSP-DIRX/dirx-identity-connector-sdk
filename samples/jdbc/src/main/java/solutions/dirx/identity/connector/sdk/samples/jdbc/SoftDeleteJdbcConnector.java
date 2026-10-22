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

package solutions.dirx.identity.connector.sdk.samples.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.ResultHandler;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.SearchResult;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.Filters;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.SearchFilter;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;
import solutions.dirx.identity.connector.sdk.jdbc.JdbcColumn;
import solutions.dirx.identity.connector.sdk.jdbc.JdbcConnector;
import solutions.dirx.identity.connector.sdk.jdbc.JdbcTable;
import solutions.dirx.identity.connector.sdk.jdbc.JdbcValues;
import solutions.dirx.identity.connector.sdk.jdbc.SqlFragment;

/**
 * JDBC connector that never removes rows. A delete sets a boolean status column (by default
 * {@code ACTIVE}) to {@code false}; inactive rows are invisible to search and read and cannot be
 * updated. New rows are created active unless the request sets the status column.
 *
 * <p>Additional property: {@value #PROP_STATUS_COLUMN}, the name of the status column. All
 * properties of {@link JdbcConnector} apply; every configured table must have the status column.
 */
@ConnectorInfo(name = "sample-jdbc-softdelete", displayName = "JDBC with soft delete (sample)",
        description = "Sample JDBC connector that deactivates rows instead of deleting them")
public class SoftDeleteJdbcConnector extends JdbcConnector {

    /** The property naming the boolean status column. */
    public static final String PROP_STATUS_COLUMN = "softDelete.statusColumn";
    /** The default status column. */
    public static final String DEFAULT_STATUS_COLUMN = "ACTIVE";

    private String statusColumn = DEFAULT_STATUS_COLUMN;

    /** Creates an uninitialized connector. */
    public SoftDeleteJdbcConnector() {
        // initialized by init()
    }

    @Override
    public void init(ConnectorContext context) throws ConnectorException {
        super.init(context);
        statusColumn = configuration().get(PROP_STATUS_COLUMN).orElse(DEFAULT_STATUS_COLUMN);
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        JdbcColumn status = status(request.objectType());
        if (request.attribute(status.attributeName()).isPresent()) {
            return super.create(request);
        }
        List<Attribute> attributes = new ArrayList<>(request.attributes());
        attributes.add(Attribute.of(status.attributeName(), Boolean.TRUE));
        return super.create(new CreateRequest(request.objectType(), request.identifier(), attributes,
                request.options()));
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        SearchFilter active = Filters.eq(status(request.objectType()).attributeName(), "true");
        SearchFilter filter = request.filter() == null ? active : Filters.and(request.filter(), active);
        return super.search(new SearchRequest(request.objectType(), request.base(), request.scope(), filter,
                request.attributesToGet(), request.pageSize(), request.pageCookie(), request.options()), handler);
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        if (!isActive(request.objectType(), request.identifier())) {
            throw notFound(request.identifier());
        }
        return super.update(request);
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        JdbcTable table = table(request.objectType());
        JdbcColumn status = status(request.objectType());
        SqlFragment sql = SqlFragment.of("UPDATE " + table.quotedName() + " SET " + status.quotedName()
                + " = ? WHERE " + table.keyColumn().quotedName() + " = ? AND " + status.quotedName() + " = ?",
                Boolean.FALSE, JdbcValues.toParameter(request.identifier().value(), table.keyColumn()), Boolean.TRUE);
        if (execute("delete " + request.identifier().value(), sql) == 0) {
            throw notFound(request.identifier());
        }
    }

    private boolean isActive(String objectType, Identifier identifier) throws ConnectorException {
        JdbcTable table = table(objectType);
        JdbcColumn status = status(objectType);
        String sql = "SELECT 1 FROM " + table.quotedName() + " WHERE " + table.keyColumn().quotedName()
                + " = ? AND " + status.quotedName() + " = ?";
        try (PreparedStatement ps = prepare(sql,
                List.of(JdbcValues.toParameter(identifier.value(), table.keyColumn()), Boolean.TRUE));
                ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            throw failure("read " + identifier.value(), e);
        }
    }

    private JdbcColumn status(String objectType) throws ConnectorException {
        JdbcColumn column = table(objectType).findColumn(statusColumn).orElseThrow(() -> new ConnectorException(
                ErrorCode.CONFIGURATION, "table of " + objectType + " has no status column " + statusColumn));
        if (!column.isBoolean()) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "status column " + statusColumn + " is not boolean");
        }
        return column;
    }

    private static ConnectorException notFound(Identifier identifier) {
        return new ConnectorException(ErrorCode.NOT_FOUND, "no active object " + identifier.value());
    }
}
