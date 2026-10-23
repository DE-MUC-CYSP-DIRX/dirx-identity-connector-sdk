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

package solutions.dirx.identity.connector.sdk.rest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;

/**
 * Encodes paging positions of REST APIs as opaque SDK page cookies.
 *
 * <p>Offset based APIs ({@code ?offset=40&limit=20}, SCIM {@code startIndex}) use
 * {@link #ofOffset(long)}/{@link #offset(String, long)}; token based APIs
 * ({@code nextPageToken}, {@code @odata.nextLink}, cursor) use
 * {@link #ofToken(String)}/{@link #token(String)}.
 */
public final class PageCookies {

    private static final String OFFSET = "o:";
    private static final String TOKEN = "t:";

    private PageCookies() {
    }

    /**
     * Encodes an offset.
     *
     * @param offset the offset of the next page
     * @return the cookie
     */
    public static String ofOffset(long offset) {
        return encode(OFFSET + offset);
    }

    /**
     * Decodes an offset cookie.
     *
     * @param cookie        the cookie, or {@code null} for the first page
     * @param initialOffset returned for {@code null} (0 for most APIs, 1 for SCIM)
     * @return the offset
     * @throws ConnectorException with {@link ErrorCode#INVALID_REQUEST} if the cookie is invalid
     */
    public static long offset(@Nullable String cookie, long initialOffset) throws ConnectorException {
        if (cookie == null || cookie.isEmpty()) {
            return initialOffset;
        }
        String s = decode(cookie, OFFSET);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw invalid(cookie);
        }
    }

    /**
     * Encodes a continuation token or next link.
     *
     * @param token the token
     * @return the cookie
     */
    public static String ofToken(String token) {
        return encode(TOKEN + token);
    }

    /**
     * Decodes a token cookie.
     *
     * @param cookie the cookie, or {@code null} for the first page
     * @return the token, or {@code null} for the first page
     * @throws ConnectorException with {@link ErrorCode#INVALID_REQUEST} if the cookie is invalid
     */
    public static @Nullable String token(@Nullable String cookie) throws ConnectorException {
        if (cookie == null || cookie.isEmpty()) {
            return null;
        }
        return decode(cookie, TOKEN);
    }

    private static String encode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String cookie, String prefix) throws ConnectorException {
        String s;
        try {
            s = new String(Base64.getUrlDecoder().decode(cookie), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid(cookie);
        }
        if (!s.startsWith(prefix)) {
            throw invalid(cookie);
        }
        return s.substring(prefix.length());
    }

    private static ConnectorException invalid(String cookie) {
        return new ConnectorException(ErrorCode.INVALID_REQUEST, "invalid page cookie '" + cookie + "'");
    }
}
