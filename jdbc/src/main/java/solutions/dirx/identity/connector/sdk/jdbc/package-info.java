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

/**
 * Helpers for connectors to relational databases: a generic table-based {@link
 * solutions.dirx.identity.connector.sdk.jdbc.JdbcConnector}, filter translation to parameterized SQL,
 * metadata-based table descriptions, value conversion and SQL error mapping. Only {@code java.sql}
 * is used; JDBC drivers are supplied by the deployment.
 */
@org.jspecify.annotations.NullMarked
package solutions.dirx.identity.connector.sdk.jdbc;
