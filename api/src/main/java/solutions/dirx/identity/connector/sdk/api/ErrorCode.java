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

package solutions.dirx.identity.connector.sdk.api;

/**
 * Classifies a {@link ConnectorException}. Hosts map these codes to their own error model;
 * DirX Identity, for example, maps them to SPML result codes.
 */
public enum ErrorCode {
    /** The object to create already exists. */
    ALREADY_EXISTS,
    /** The addressed object does not exist. */
    NOT_FOUND,
    /** A value to add is already present. */
    ATTRIBUTE_OR_VALUE_EXISTS,
    /** An attribute or value to delete does not exist. */
    NO_SUCH_ATTRIBUTE,
    /** The request violates the schema of the target. */
    SCHEMA_VIOLATION,
    /** The request is malformed or uses an unsupported feature. */
    INVALID_REQUEST,
    /** The connector does not support the requested operation. */
    UNSUPPORTED_OPERATION,
    /** The target rejected the credentials of the connector. */
    INVALID_CREDENTIALS,
    /** The connector is not allowed to perform the operation. */
    PERMISSION_DENIED,
    /** The target could not be reached. Usually raised as {@link RetryableConnectorException}. */
    CONNECTION_FAILED,
    /** The target did not answer in time. Usually raised as {@link RetryableConnectorException}. */
    TIMEOUT,
    /** The connector configuration is invalid. */
    CONFIGURATION,
    /** Any other error. */
    OTHER
}
