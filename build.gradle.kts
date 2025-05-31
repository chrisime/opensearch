import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.time.Instant

plugins {
    kotlin("jvm") version "2.1.21"

    id("com.github.jmongard.git-semver-plugin") version "0.16.0"

    id("org.jetbrains.kotlinx.kover") version "0.9.1"

    `java-library`

    `maven-publish`

    signing

    idea
}

description = "OpenSearch Client Library"
group = "com.github.chrisime.search"
version = semver.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.4"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("org.slf4j:slf4j-api:2.0.17")

    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")

    api("org.opensearch.client:opensearch-java:2.24.0")
    implementation("org.opensearch.client:opensearch-rest-client:2.19.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")

    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
    testImplementation("org.testcontainers:testcontainers:1.21.0")

    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
}

semver {
}

tasks {
    java {
        sourceCompatibility = JavaVersion.toVersion("17")
        targetCompatibility = JavaVersion.toVersion("17")

        withSourcesJar()
        withJavadocJar()
    }

    kotlin {
        jvmToolchain {
            languageVersion = JavaLanguageVersion.of("17")
            implementation = JvmImplementation.VENDOR_SPECIFIC
        }

        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("17")
            apiVersion = KotlinVersion.KOTLIN_2_1
            languageVersion = KotlinVersion.KOTLIN_2_1
        }
    }

    idea {
        module {
            languageLevel = IdeaLanguageLevel("17")
            targetVersion = "17"
            targetBytecodeVersion = JavaVersion.toVersion("17")

            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }

    jar {
        enabled = true
        archiveBaseName = "opensearch-client"
        archiveVersion = "$version"

        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to group,

                "Specification-Title" to project.name,
                "Specification-Version" to project.version,
                "Specification-Vendor" to group,

                "Built-By" to System.getProperty("user.name"),
                "Built-Date" to Instant.now().toString(),
                "Built-JDK" to System.getProperty("java.version"),
                "Built-Gradle" to gradle.gradleVersion,

                "Git-Commit" to (System.getenv("CI_COMMIT_SHA") ?: "unknown"),
                "Git-Branch" to (System.getenv("CI_COMMIT_BRANCH") ?: "unknown"),

                "Created-By" to "Gradle ${gradle.gradleVersion}",
                "Bundle-Name" to project.name,
                "Bundle-Description" to project.description,
                "Bundle-Version" to project.version,
            )
        }
    }

    test {
        jvmArgs = listOf("-Xshare:off")

        maxParallelForks = Runtime.getRuntime().availableProcessors()

        useJUnitPlatform()

        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            displayGranularity = 2
            showExceptions = true
            showCauses = true
            showStackTraces = true
            showStandardStreams = false
        }
    }
}

signing {
    useInMemoryPgpKeys(
        System.getenv("SIGNING_KEY_ID"),
        System.getenv("SIGNING_SECRET_KEY"),
        System.getenv("SIGNING_PASSWORD")
    )
    sign(publishing.publications)
}

publishing {
    publications {
        create<MavenPublication>("opensearchClient") {
            from(components["java"])

            pom {
                name = "OpenSearch Client"
                description = "An abstraction library for OpenSearch Client"
                url = "https://github.com/chrisime/search.git"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://opensource.org/licenses/Apache-2.0"
                    }
                }

                developers {
                    developer {
                        name = "Christian Meyer"
                        email = "christian.meyer@gmail.com"
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/deinusername/opensearch-client.git")
                    developerConnection.set("scm:git:ssh://github.com:deinusername/opensearch-client.git")
                    url.set("https://github.com/deinusername/opensearch-client")
                }
            }
        }
    }

    repositories {
        maven("central") {
            name = "central"
            url = uri("https://central.sonatype.com/api/v1/publisher/upload")

            credentials {
                username = System.getenv("CENTRAL_PORTAL_USERNAME")
                password = System.getenv("CENTRAL_PORTAL_PASSWORD")
            }
        }
    }
}

tasks.register("publishToCentral") {
    dependsOn("publishOpensearchClientPublicationToCentralRepository")
    description = "Publishes to Maven Central via Central Portal"
    group = "publishing"
}
