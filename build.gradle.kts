import io.gitlab.arturbosch.detekt.Detekt
import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    kotlin("jvm").version(Versions.Compile.kotlin)
    kotlin("plugin.spring").version(Versions.Compile.kotlin)

    id("org.jlleitschuh.gradle.ktlint").version(Versions.Plugins.ktlint)
    id("io.gitlab.arturbosch.detekt").version(Versions.Plugins.detekt)
    id("org.unbroken-dome.test-sets").version(Versions.Plugins.testSets)
//    id("com.adarshr.test-logger").version(Versions.Plugins.testLogger) // does not work with current version of Kotlin
    id("org.springframework.boot").version(Versions.Plugins.springBoot)
    id("io.spring.dependency-management").version(Versions.Plugins.springDependencyManagement)
    id("com.google.cloud.tools.jib").version(Versions.Plugins.jib)
    id("org.asciidoctor.jvm.convert").version(Versions.Plugins.asciiDoctor)
    id("org.flywaydb.flyway").version(Versions.Plugins.flyway)
    id("nu.studer.jooq").version(Versions.Plugins.jooq)
    id("application")

    idea
    jacoco
}

extensions.configure(KtlintExtension::class.java) {
    version.set(Versions.Tools.ktlint)
}

group = "com.ampnet"
version = Versions.project
java.sourceCompatibility = Versions.Compile.sourceCompatibility
java.targetCompatibility = Versions.Compile.targetCompatibility

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        maven(url = "https://jitpack.io")
    }
}

testSets {
    Configurations.Tests.testSets.forEach { create(it) }
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("$buildDir/generated/sources/jooq/main/kotlin")
    }
}

jib {
    val dockerUsername: String = System.getenv("DOCKER_USERNAME") ?: "DOCKER_USERNAME"
    val dockerPassword: String = System.getenv("DOCKER_PASSWORD") ?: "DOCKER_PASSWORD"
    to {
        image = "ampnet/${rootProject.name}:$version"
        auth {
            username = dockerUsername
            password = dockerPassword
        }
        tags = setOf("latest")
    }
    container {
        creationTime = "USE_CURRENT_TIMESTAMP"
        mainClass = "com.ampnet.payoutservice.PayoutServiceApplicationKt"
    }
    from {
        image = "${Configurations.Docker.baseImage}:${Configurations.Docker.tag}@${Configurations.Docker.digest}"
    }
}

val flywayMigration by configurations.creating

fun DependencyHandler.integTestImplementation(dependencyNotation: Any): Dependency? =
    add("integTestImplementation", dependencyNotation)

fun DependencyHandler.kaptIntegTest(dependencyNotation: Any): Dependency? =
    add("kaptIntegTest", dependencyNotation)

fun DependencyHandler.apiTestImplementation(dependencyNotation: Any): Dependency? =
    add("apiTestImplementation", dependencyNotation)

fun DependencyHandler.kaptApiTest(dependencyNotation: Any): Dependency? =
    add("kaptApiTest", dependencyNotation)

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.postgresql:postgresql")
    flywayMigration(Configurations.Database.driverDependency)
    jooqGenerator(Configurations.Database.driverDependency)

    implementation("org.web3j:core:${Versions.Dependencies.web3j}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.Dependencies.okHttp}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Dependencies.kotlinCoroutines}")
    implementation("io.github.microutils:kotlin-logging-jvm:${Versions.Dependencies.kotlinLogging}")
    implementation("com.github.AMPnet:jwt:${Versions.Dependencies.jwt}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Versions.Dependencies.mockitoKotlin}")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:${Versions.Dependencies.assertk}")
    testImplementation("org.testcontainers:testcontainers:${Versions.Dependencies.testContainers}")
    testImplementation("org.testcontainers:postgresql:${Versions.Dependencies.testContainers}")
    testImplementation("com.github.tomakehurst:wiremock:${Versions.Dependencies.wireMock}")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    integTestImplementation(sourceSets.test.get().output)

    apiTestImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    apiTestImplementation("org.springframework.security:spring-security-test")
    apiTestImplementation(sourceSets.test.get().output)
}

flyway {
    url = Configurations.Database.url
    user = Configurations.Database.user
    password = Configurations.Database.password
    schemas = arrayOf(Configurations.Database.schema)
    configurations = arrayOf("flywayMigration")
}

jooq {
    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = Configurations.Database.driverClass
                    url = Configurations.Database.url
                    user = Configurations.Database.user
                    password = Configurations.Database.password
                }

                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        inputSchema = Configurations.Database.schema
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isImmutableInterfaces = true
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "com.ampnet.payoutservice.generated.jooq"
                        directory = "$buildDir/generated/sources/jooq/main/kotlin"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

tasks.withType<JooqGenerate> {
    dependsOn(tasks["flywayMigrate"])
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = Configurations.Compile.compilerArgs
        jvmTarget = Versions.Compile.jvmTarget
    }
    dependsOn.add(tasks["generateJooq"])
}

tasks.withType<Test> {
    useJUnitPlatform()
}

task("fullTest") {
    val allTests = listOf(tasks.test.get()) + Configurations.Tests.testSets.map { tasks[it] }
    for (i in 0 until (allTests.size - 1)) {
        allTests[i + 1].mustRunAfter(allTests[i])
    }
    dependsOn(*allTests.toTypedArray())
}

jacoco.toolVersion = Versions.Tools.jacoco
tasks.withType<JacocoReport> {
    val allTestExecFiles = (listOf("test") + Configurations.Tests.testSets)
        .map { "$buildDir/jacoco/$it.exec" }
    executionData(*allTestExecFiles.toTypedArray())

    reports {
        xml.isEnabled = true
        xml.destination = file("$buildDir/reports/jacoco/report.xml")
        csv.isEnabled = false
        html.destination = file("$buildDir/reports/jacoco/html")
    }
    sourceDirectories.setFrom(listOf(file("${project.projectDir}/src/main/kotlin")))
    classDirectories.setFrom(
        fileTree("$buildDir/classes/kotlin/main").apply {
            exclude("com/ampnet/payoutservice/generated/**")
        }
    )
    dependsOn(tasks["fullTest"])
}

tasks.withType<JacocoCoverageVerification> {
    val allTestExecFiles = (listOf("test") + Configurations.Tests.testSets)
        .map { "$buildDir/jacoco/$it.exec" }
    executionData(*allTestExecFiles.toTypedArray())

    sourceDirectories.setFrom(listOf(file("${project.projectDir}/src/main/kotlin")))
    classDirectories.setFrom(
        fileTree("$buildDir/classes/kotlin/main").apply {
            exclude("com/ampnet/payoutservice/generated/**")
        }
    )

    violationRules {
        rule {
            limit {
                minimum = Configurations.Tests.minimumCoverage
            }
        }
    }
    mustRunAfter(tasks.jacocoTestReport)
}

detekt {
    source = files("src/main/kotlin")
    config = files("detekt-config.yml")
}

tasks.withType<Detekt> {
    exclude("com/ampnet/payoutservice/generated/**")
}

ktlint {
    filter {
        exclude("com/ampnet/payoutservice/generated/**")
    }
}

tasks.asciidoctor {
    attributes(
        mapOf(
            "snippets" to file("build/generated-snippets"),
            "version" to version,
            "date" to SimpleDateFormat("yyyy-MM-dd").format(Date())
        )
    )
    dependsOn(tasks["fullTest"])
}

tasks.register<Copy>("copyDocs") {
    from(file("$buildDir/docs/asciidoc"))
    into(file("src/main/resources/static/docs"))
    dependsOn(tasks.asciidoctor)
}

task("qualityCheck") {
    dependsOn(tasks.ktlintCheck, tasks.detekt, tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}
