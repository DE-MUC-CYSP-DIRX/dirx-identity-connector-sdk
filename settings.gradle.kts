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

// Standalone build of the DirX Identity Connector SDK.
// This repository is the source of truth for the public SDK.
// Hosts can consume published artifacts or include this standalone build.

rootProject.name = "dirx-dxi-connector-sdk"

dependencyResolutionManagement {
    repositories {
        // Optional Maven mirror (e.g. a corporate Nexus), configured via environment:
        //   SDK_MAVEN_MIRROR_URL, SDK_MAVEN_MIRROR_USERNAME, SDK_MAVEN_MIRROR_PASSWORD
        // Without it, dependencies are resolved from Maven Central.
        val mirrorUrl = System.getenv("SDK_MAVEN_MIRROR_URL")
        if (!mirrorUrl.isNullOrEmpty()) {
            maven {
                name = "mirror"
                url = uri(mirrorUrl)
                val user = System.getenv("SDK_MAVEN_MIRROR_USERNAME")
                if (!user.isNullOrEmpty()) {
                    credentials {
                        username = user
                        password = System.getenv("SDK_MAVEN_MIRROR_PASSWORD")
                    }
                }
            }
        }
        mavenCentral()
    }
}

// Project names equal the published artifactIds, so that Gradle substitutes them
// automatically when this build is used as an included build.
fun sdkModule(dir: String) {
    val name = "dirx-dxi-connector-sdk-$dir"
    include(name)
    project(":$name").projectDir = file(dir)
}

sdkModule("api")
sdkModule("bom")
sdkModule("test")
sdkModule("rest")
sdkModule("scim")
sdkModule("jdbc")

// Sample connectors (not published); see samples/README.md.
listOf("csv", "rest", "scim", "jdbc").forEach { sample ->
    val name = "dirx-dxi-connector-sdk-sample-$sample"
    include(name)
    project(":$name").projectDir = file("samples/$sample")
}
