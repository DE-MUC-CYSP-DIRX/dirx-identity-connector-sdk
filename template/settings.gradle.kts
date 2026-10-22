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
