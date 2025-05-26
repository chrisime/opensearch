import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.time.Instant

plugins {
    kotlin("jvm") version "2.1.21"

    id("com.github.jmongard.git-semver-plugin") version "0.16.0"

    `java-library`

    `maven-publish`

    idea
}

description = "OpenSearch Client Abstraction Library"
group = "com.dertouristik.portfolio"
version = semver.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.2"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("org.slf4j:slf4j-api:2.0.17")

    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")

    api("org.opensearch.client:opensearch-java:2.24.0")
    implementation("org.opensearch.client:opensearch-rest-client:2.19.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

semver {
}

tasks {
    java {
        sourceCompatibility = JavaVersion.toVersion("21")
        targetCompatibility = JavaVersion.toVersion("21")

        withSourcesJar()
        withJavadocJar()
    }

    kotlin {
        jvmToolchain {
            languageVersion = JavaLanguageVersion.of("21")
            implementation = JvmImplementation.VENDOR_SPECIFIC
            vendor = JvmVendorSpec.BELLSOFT
        }

        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("21")
            apiVersion = KotlinVersion.KOTLIN_2_1
            languageVersion = KotlinVersion.KOTLIN_2_1
        }
    }

    idea {
        module {
            languageLevel = IdeaLanguageLevel("21")
            targetVersion = "21"
            targetBytecodeVersion = JavaVersion.toVersion("21")

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
        useJUnitPlatform()
    }
}

publishing {
    publications {
        create<MavenPublication>("opensearchClient") {
            from(components["java"])

            pom {
                name.set("OpenSearch Client")
                description.set("An abstraction library for OpenSearchClient")
                url.set("https://gitlab.dto.rocks/dto/dtpa/opensearch")

                developers {
                    developer {
                        name.set("Christian Meyer")
                        email.set("christian.meyer@dertour.com")
                        organization.set("DER GmbH")
                    }
                }

                scm {
                    url.set("https://gitlab.dto.rocks/dto/dtpa/opensearch.git")
                }
            }
        }
    }

    repositories {
        maven {
            val baseUri = System.getenv("CI_API_V4_URL") ?: "https://gitlab.dto.rocks/api/v4"
            val projectId = System.getenv("CI_PROJECT_ID") ?: "529"
            val token = System.getenv("CI_JOB_TOKEN") ?: "api-token"

            url = uri("$baseUri/projects/$projectId/packages/maven")

            credentials(HttpHeaderCredentials::class) {
                name = "Job-Token"
                value = token
            }

            authentication {
                create("header", HttpHeaderAuthentication::class)
            }
        }
    }
}
