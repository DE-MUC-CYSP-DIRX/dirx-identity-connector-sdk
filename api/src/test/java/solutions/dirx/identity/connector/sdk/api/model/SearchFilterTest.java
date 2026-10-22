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

package solutions.dirx.identity.connector.sdk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;

class SearchFilterTest {

    @Test
    void rendersLdapStringWithEscaping() {
        SearchFilter f = Filters.and(
                Filters.eq("cn", "a*b(c)\\"),
                Filters.or(Filters.present("mail"), Filters.not(Filters.startsWith("sn", "Mül"))),
                new SearchFilter.Substring("uid", "a", List.of("b", "c"), "d"),
                Filters.ge("age", "18"), Filters.le("age", "65"), Filters.approx("cn", "x"));
        assertEquals("(&(cn=a\\2ab\\28c\\29\\5c)(|(mail=*)(!(sn=Mül*)))(uid=a*b*c*d)(age>=18)(age<=65)(cn~=x))",
                f.toLdapString());
    }

    @Test
    void rejectsInvalidFilters() {
        assertThrows(IllegalArgumentException.class, () -> new SearchFilter.And(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Filters.eq(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> new SearchFilter.Substring("cn", null, null, ""));
        assertThrows(NullPointerException.class, () -> Filters.eq("cn", null));
    }

    @Test
    void translatorDispatchesAllNodes() throws ConnectorException {
        FilterTranslator<String> t = new FilterTranslator<>() {
            @Override
            public String and(List<String> operands) {
                return String.join(" and ", operands);
            }

            @Override
            public String equalTo(String attribute, String value) {
                return attribute + " eq " + value;
            }

            @Override
            public String substring(String attribute, String initial, List<String> any, String fin) {
                return attribute + " sw " + initial;
            }
        };
        assertEquals("a eq 1 and b sw x", t.translate(Filters.and(Filters.eq("a", "1"), Filters.startsWith("b", "x"))));
        ConnectorException e = assertThrows(ConnectorException.class, () -> t.translate(Filters.present("a")));
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
    }
}
