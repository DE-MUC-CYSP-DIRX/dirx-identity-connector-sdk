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

import java.net.URI;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.RetryableConnectorException;

/**
 * Converts an unsuccessful HTTP response into a {@link ConnectorException}.
 *
 * <p>Connectors typically wrap {@link #DEFAULT} to refine the mapping for a specific API,
 * e.g. to detect "already exists" errors that the target reports with status 400.
 */
@FunctionalInterface
public interface HttpErrorMapper {

    /** Maximum number of body characters included in exception messages. */
    int MAX_BODY_IN_MESSAGE = 500;

    /**
     * The default mapping.
     * <table>
     * <caption>HTTP status to error code</caption>
     * <tr><th>Status</th><th>Error code</th></tr>
     * <tr><td>400, 422</td><td>INVALID_REQUEST</td></tr>
     * <tr><td>401</td><td>INVALID_CREDENTIALS</td></tr>
     * <tr><td>403</td><td>PERMISSION_DENIED</td></tr>
     * <tr><td>404, 410</td><td>NOT_FOUND</td></tr>
     * <tr><td>405, 501</td><td>UNSUPPORTED_OPERATION</td></tr>
     * <tr><td>409</td><td>ALREADY_EXISTS</td></tr>
     * <tr><td>408, 504</td><td>TIMEOUT (retryable)</td></tr>
     * <tr><td>429, 502, 503</td><td>CONNECTION_FAILED (retryable)</td></tr>
     * <tr><td>other</td><td>OTHER</td></tr>
     * </table>
     */
    HttpErrorMapper DEFAULT = (method, uri, response) -> {
        int status = response.statusCode();
        String message = message(method, uri, response);
        return switch (status) {
            case 400, 422 -> new ConnectorException(ErrorCode.INVALID_REQUEST, message);
            case 401 -> new ConnectorException(ErrorCode.INVALID_CREDENTIALS, message);
            case 403 -> new ConnectorException(ErrorCode.PERMISSION_DENIED, message);
            case 404, 410 -> new ConnectorException(ErrorCode.NOT_FOUND, message);
            case 405, 501 -> new ConnectorException(ErrorCode.UNSUPPORTED_OPERATION, message);
            case 409 -> new ConnectorException(ErrorCode.ALREADY_EXISTS, message);
            case 408, 504 -> new RetryableConnectorException(ErrorCode.TIMEOUT, message);
            case 429, 502, 503 -> new RetryableConnectorException(ErrorCode.CONNECTION_FAILED, message);
            default -> new ConnectorException(ErrorCode.OTHER, message);
        };
    };

    /**
     * Maps an unsuccessful response.
     *
     * @param method   the HTTP method of the request
     * @param uri      the request URI
     * @param response the response
     * @return the exception to throw, never {@code null}
     */
    ConnectorException map(String method, URI uri, RestResponse response);

    /**
     * Builds a message of the form {@code "GET https://host/path failed with HTTP 404: <body>"}.
     * The body is truncated to {@link #MAX_BODY_IN_MESSAGE} characters.
     *
     * @param method   the HTTP method
     * @param uri      the request URI
     * @param response the response
     * @return the message
     */
    static String message(String method, URI uri, RestResponse response) {
        String body = response.body().strip();
        if (body.length() > MAX_BODY_IN_MESSAGE) {
            body = body.substring(0, MAX_BODY_IN_MESSAGE) + "...";
        }
        String query = uri.getRawQuery();
        String target = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath()
                + (query != null ? "?" + query : "");
        return method + " " + target + " failed with HTTP " + response.statusCode()
                + (body.isEmpty() ? "" : ": " + body);
    }
}
