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

// Connector project created from the DirX Identity Connector SDK template.
// Rename the project; the name becomes the file name of the connector bundle.
rootProject.name = "example-connector"

dependencyResolutionManagement {
    repositories {
        // Optional repository holding SDK builds that are not on Maven Central yet,
        // e.g. -PconnectorSdkRepository=file:///path/to/sdk/build/staging-repo
        val sdkRepository = providers.gradleProperty("connectorSdkRepository").orNull
        if (!sdkRepository.isNullOrBlank()) {
            maven {
                name = "connectorSdk"
                url = uri(sdkRepository)
            }
        }
        mavenCentral()
    }
}
