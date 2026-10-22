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

import java.net.URI;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.rest.HttpErrorMapper;
import solutions.dirx.identity.connector.sdk.rest.RestResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Maps SCIM error responses (RFC 7644, section 3.12) to connector exceptions.
 *
 * <p>The {@code scimType} of the error refines the mapping of {@link HttpErrorMapper#DEFAULT}:
 * {@code uniqueness} becomes {@link ErrorCode#ALREADY_EXISTS}, {@code mutability}
 * becomes {@link ErrorCode#SCHEMA_VIOLATION}, {@code noTarget} becomes
 * {@link ErrorCode#NO_SUCH_ATTRIBUTE}, and the other types become
 * {@link ErrorCode#INVALID_REQUEST}.
 */
public final class ScimErrorMapper implements HttpErrorMapper {

    /** The shared instance. */
    public static final ScimErrorMapper INSTANCE = new ScimErrorMapper();

    private ScimErrorMapper() {
    }

    @Override
    public ConnectorException map(String method, URI uri, RestResponse response) {
        ConnectorException fallback = DEFAULT.map(method, uri, response);
        if (fallback.isRetryable()) {
            return fallback;
        }
        String scimType = scimType(response);
        if (scimType == null) {
            return fallback;
        }
        ErrorCode code = switch (scimType) {
            case "uniqueness" -> ErrorCode.ALREADY_EXISTS;
            case "mutability" -> ErrorCode.SCHEMA_VIOLATION;
            case "noTarget" -> ErrorCode.NO_SUCH_ATTRIBUTE;
            case "invalidFilter", "tooMany", "invalidSyntax", "invalidPath", "invalidValue", "invalidVers",
                 "sensitive" -> ErrorCode.INVALID_REQUEST;
            default -> fallback.getErrorCode();
        };
        return new ConnectorException(code, fallback.getMessage());
    }

    private static String scimType(RestResponse response) {
        try {
            JsonNode type = response.json().path("scimType");
            return type.isString() ? type.asString() : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
