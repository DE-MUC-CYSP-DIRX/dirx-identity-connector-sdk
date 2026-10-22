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

/**
 * Verifies the open-source conventions of every SDK module:
 *  - all sources live below the package `solutions.dirx.identity.connector.sdk`,
 *  - every source file carries the Apache-2.0 license header,
 *  - no source imports DirX Identity internal packages (only the SDK's own packages),
 *  - no runtime dependency comes from DirX Identity internal groups (only SDK modules).
 */
abstract class VerifySdkConventions : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val runtimeComponents: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    private val requiredPackage = "solutions.dirx.identity.connector.sdk"
    private val packageRegex = Regex("""(?m)^\s*package\s+([\w.]+)\s*;""")
    private val forbiddenImport = Regex(
        """(?m)^\s*import\s+(static\s+)?(siemens|com\.siemens|net\.atos|org\.exolab|solutions\.dirx\.identity(?!\.connector\.sdk\.))\."""
    )
    private val sdkArtifactPrefix = "solutions.dirx.identity:dirx-dxi-connector-sdk-"
    private val forbiddenGroups = listOf(
        "solutions.dirx.identity", "com.siemens", "siemens", "net.atos", "org.codehaus.castor", "castor"
    )

    @TaskAction
    fun verify() {
        val violations = mutableListOf<String>()
        sources.asFileTree.matching { include("**/*.java") }.forEach { f ->
            val text = f.readText()
            val head = text.lineSequence().take(20).joinToString("\n")
            if (!head.contains("Licensed under the Apache License, Version 2.0")) {
                violations += "${f.name}: missing Apache-2.0 license header"
            }
            val pkg = packageRegex.find(text)?.groupValues?.get(1)
            if (pkg == null || !(pkg == requiredPackage || pkg.startsWith("$requiredPackage."))) {
                violations += "${f.name}: package '$pkg' is not below '$requiredPackage'"
            }
            forbiddenImport.findAll(text).forEach { m ->
                violations += "${f.name}: forbidden import '${m.value.trim()}'"
            }
        }
        runtimeComponents.get().forEach { id ->
            val internal = forbiddenGroups.any { id.startsWith("$it.") || id.startsWith("$it:") }
            if (internal && !id.startsWith(sdkArtifactPrefix)) {
                violations += "forbidden runtime dependency '$id'"
            }
        }
        report.get().asFile.writeText(violations.joinToString("\n"))
        if (violations.isNotEmpty()) {
            throw GradleException("SDK convention violations:\n  " + violations.joinToString("\n  "))
        }
    }
}

val sdkLibs = libs

val javaModules = subprojects.filter { it.name != "dirx-dxi-connector-sdk-bom" }

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    extensions.configure<PublishingExtension> {
        repositories {
            // Local staging repository, used to verify consumption by Maven/Gradle builds.
            maven {
                name = "staging"
                url = uri(rootProject.layout.buildDirectory.dir("staging-repo"))
            }
            val publishUrl = System.getenv("SDK_PUBLISH_URL")
            if (!publishUrl.isNullOrEmpty()) {
                maven {
                    name = "release"
                    url = uri(publishUrl)
                    credentials {
                        username = System.getenv("SDK_PUBLISH_USERNAME")
                        password = System.getenv("SDK_PUBLISH_PASSWORD")
                    }
                }
            }
        }
        publications.withType<MavenPublication>().configureEach {
            pom {
                url.set(providers.gradleProperty("sdkProjectUrl"))
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                organization {
                    name.set("Atos Secure Digital GmbH")
                }
                developers {
                    developer {
                        name.set("DirX Identity Team")
                        organization.set("Atos Secure Digital GmbH")
                    }
                }
                scm {
                    url.set(providers.gradleProperty("sdkProjectUrl"))
                }
            }
        }
    }

    // Artifacts are signed (required by Maven Central) when an ASCII-armored PGP key is provided.
    val signingKey = System.getenv("SDK_SIGNING_KEY")
    if (!signingKey.isNullOrEmpty()) {
        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(signingKey, System.getenv("SDK_SIGNING_PASSWORD"))
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}

configure(javaModules) {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing", "-Werror"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:all,-missing", "-quiet")
            links("https://docs.oracle.com/en/java/javase/21/docs/api/")
        }
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes(
                "Automatic-Module-Name" to project.name.removePrefix("dirx-dxi-").replace('-', '.')
                    .let { "solutions.dirx.identity.$it" },
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Atos Secure Digital GmbH"
            )
        }
        from(rootProject.files("LICENSE", "NOTICE")) { into("META-INF") }
    }

    dependencies {
        "testImplementation"(platform(sdkLibs.junit.bom))
        "testImplementation"(sdkLibs.junit.jupiter)
        "testRuntimeOnly"(sdkLibs.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Samples are source examples only and are not published.
    if (!project.name.contains("-sample-")) extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set(project.description ?: project.name)
                }
            }
        }
    }

    val verifySdkConventions = tasks.register<VerifySdkConventions>("verifySdkConventions") {
        group = "verification"
        description = "Verifies package, license header and dependency conventions of the SDK."
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        sources.from(sourceSets.map { it.allJava.sourceDirectories })
        runtimeComponents.set(configurations.named("runtimeClasspath").map { cfg ->
            cfg.incoming.resolutionResult.allComponents.map { it.id.displayName }
        })
        report.set(layout.buildDirectory.file("reports/sdk-conventions.txt"))
    }
    tasks.named("check") { dependsOn(verifySdkConventions) }
}

// Builds the connector template (template/) against the SDK modules of this build,
// published to the local staging repository. Verifies that the template, i.e. a
// consumer outside this build, can resolve and use the published artifacts.
tasks.register<GradleBuild>("verifyTemplate") {
    group = "verification"
    description = "Builds and tests the connector template against the staged SDK artifacts."
    dependsOn(subprojects.mapNotNull { p ->
        if (p.name.contains("-sample-")) null else "${p.path}:publishAllPublicationsToStagingRepository"
    })
    dir = file("template")
    tasks = listOf("clean", "build")
    startParameter.projectProperties = mapOf(
        "connectorSdkRepository" to layout.buildDirectory.dir("staging-repo").get().asFile.toURI().toString(),
        "connectorSdkVersion" to project.version.toString(),
    )
}
