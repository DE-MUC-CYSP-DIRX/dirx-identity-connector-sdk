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
