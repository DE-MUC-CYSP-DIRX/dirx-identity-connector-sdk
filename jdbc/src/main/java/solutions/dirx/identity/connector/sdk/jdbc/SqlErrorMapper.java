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

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.Set;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.RetryableConnectorException;

/**
 * Maps {@link SQLException}s to {@link ConnectorException}s, mainly based on the SQLSTATE.
 *
 * <ul>
 *   <li>unique constraint violations (SQLSTATE {@code 23505}, or vendor codes of MySQL/MariaDB, Oracle,
 *       SQL Server and Db2) &rarr; {@link ErrorCode#ALREADY_EXISTS}</li>
 *   <li>other integrity constraint violations (class {@code 23}) and data exceptions (class {@code 22})
 *       &rarr; {@link ErrorCode#SCHEMA_VIOLATION}</li>
 *   <li>authorization failures (class {@code 28}) &rarr; {@link ErrorCode#INVALID_CREDENTIALS}</li>
 *   <li>syntax or access rule violations (class {@code 42}) &rarr; {@link ErrorCode#PERMISSION_DENIED}
 *       for {@code 42501}, else {@link ErrorCode#INVALID_REQUEST}</li>
 *   <li>connection exceptions (class {@code 08}), recoverable and transient exceptions &rarr; retryable
 *       {@link ErrorCode#CONNECTION_FAILED}</li>
 *   <li>timeouts &rarr; retryable {@link ErrorCode#TIMEOUT}</li>
 * </ul>
 */
public final class SqlErrorMapper {

    private static final Set<Integer> MYSQL_DUPLICATE = Set.of(1062, 1586);
    private static final Set<Integer> SQLSERVER_DUPLICATE = Set.of(2601, 2627);

    private SqlErrorMapper() {
    }

    /**
     * Maps an exception.
     *
     * @param operation a short description of the failed operation, used in the message
     * @param e         the exception
     * @return the connector exception
     */
    public static ConnectorException map(String operation, SQLException e) {
        String state = e.getSQLState() != null ? e.getSQLState() : "";
        String message = operation + " failed: " + e.getMessage() + (state.isEmpty() ? "" : " (SQLSTATE " + state + ")");
        if (e instanceof SQLTimeoutException) {
            return new RetryableConnectorException(ErrorCode.TIMEOUT, message, e);
        }
        if (isDuplicate(e, state)) {
            return new ConnectorException(ErrorCode.ALREADY_EXISTS, message, e);
        }
        String cls = state.length() >= 2 ? state.substring(0, 2) : "";
        return switch (cls) {
            case "23", "22" -> new ConnectorException(ErrorCode.SCHEMA_VIOLATION, message, e);
            case "28" -> new ConnectorException(ErrorCode.INVALID_CREDENTIALS, message, e);
            case "42" -> new ConnectorException("42501".equals(state) ? ErrorCode.PERMISSION_DENIED
                    : ErrorCode.INVALID_REQUEST, message, e);
            case "08" -> new RetryableConnectorException(ErrorCode.CONNECTION_FAILED, message, e);
            case "0A" -> new ConnectorException(ErrorCode.UNSUPPORTED_OPERATION, message, e);
            default -> {
                if (e instanceof SQLNonTransientConnectionException || e instanceof SQLRecoverableException
                        || e instanceof SQLTransientException) {
                    yield new RetryableConnectorException(ErrorCode.CONNECTION_FAILED, message, e);
                }
                yield new ConnectorException(ErrorCode.OTHER, message, e);
            }
        };
    }

    /**
     * Tells whether the exception indicates that the connection is no longer usable.
     *
     * @param e the exception
     * @return {@code true} for connection failures
     */
    public static boolean isConnectionFailure(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("08")) || e instanceof SQLNonTransientConnectionException
                || e instanceof SQLRecoverableException;
    }

    private static boolean isDuplicate(SQLException e, String state) {
        if ("23505".equals(state)) {
            return true;
        }
        if (!state.startsWith("23")) {
            return false;
        }
        int code = e.getErrorCode();
        return MYSQL_DUPLICATE.contains(code) || SQLSERVER_DUPLICATE.contains(code)
                || code == 1 /* Oracle ORA-00001 */ || code == -803 /* Db2 */;
    }
}
