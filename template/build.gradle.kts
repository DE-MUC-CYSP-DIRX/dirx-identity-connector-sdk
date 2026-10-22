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

plugins {
    java
}

val sdkVersion = providers.gradleProperty("connectorSdkVersion").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // The BOM aligns the versions of all SDK modules.
    implementation(platform("solutions.dirx.identity:dirx-dxi-connector-sdk-bom:$sdkVersion"))
    testImplementation(platform("solutions.dirx.identity:dirx-dxi-connector-sdk-bom:$sdkVersion"))

    // The SDK API is provided by DirX Identity at runtime and must not be bundled.
    compileOnly("solutions.dirx.identity:dirx-dxi-connector-sdk-api")

    // Optional helper modules. Unlike the API they are bundled with the connector.
    // implementation("solutions.dirx.identity:dirx-dxi-connector-sdk-rest")
    // implementation("solutions.dirx.identity:dirx-dxi-connector-sdk-scim")
    // implementation("solutions.dirx.identity:dirx-dxi-connector-sdk-jdbc")

    testImplementation("solutions.dirx.identity:dirx-dxi-connector-sdk-api")
    testImplementation("solutions.dirx.identity:dirx-dxi-connector-sdk-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

// Helper modules depend on the SDK API; keep it out of the bundle in any case.
configurations.runtimeClasspath {
    exclude(group = "solutions.dirx.identity", module = "dirx-dxi-connector-sdk-api")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

// Deployable bundle: the connector jar plus its runtime dependencies, without the SDK API.
// Unpack it into the connector directory of DirX Identity (see README.md).
val connectorBundle by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Assembles the deployable connector bundle."
    archiveFileName.set("${project.name}-${project.version}-connector.zip")
    destinationDirectory.set(layout.buildDirectory.dir("connector"))
    into(project.name) {
        from(tasks.jar)
        from(configurations.runtimeClasspath)
    }
}

tasks.assemble {
    dependsOn(connectorBundle)
}
