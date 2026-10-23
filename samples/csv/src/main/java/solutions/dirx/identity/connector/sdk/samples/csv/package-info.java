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
 * Sample connector that manages accounts in a CSV file. It depends on the SDK API only and shows
 * how to implement every operation interface, including client-side filtering with {@link
 * solutions.dirx.identity.connector.sdk.api.model.FilterEvaluator} and offset-based paging.
 */
@org.jspecify.annotations.NullMarked
package solutions.dirx.identity.connector.sdk.samples.csv;
