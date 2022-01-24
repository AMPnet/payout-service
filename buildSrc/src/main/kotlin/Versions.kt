import org.gradle.api.JavaVersion

object Versions {

    const val project = "0.0.1"

    object Compile {
        const val kotlin = "1.6.10"
        val sourceCompatibility = JavaVersion.VERSION_11
        val targetCompatibility = JavaVersion.VERSION_11
        val jvmTarget = targetCompatibility.name.removePrefix("VERSION_").replace('_', '.')
    }

    object Plugins {
        const val ktlint = "10.2.1"
        const val detekt = "1.19.0"
        const val testSets = "4.0.0"
        const val testLogger = "3.1.0"
        const val springBoot = "2.6.2"
        const val springDependencyManagement = "1.0.11.RELEASE"
        const val flyway = "8.4.1"
        const val jooq = "6.0.1"
        const val jib = "3.1.4"
        const val asciiDoctor = "3.3.2"
    }

    object Tools {
        const val ktlint = "0.43.2"
        const val jacoco = "0.8.7"
        const val solidity = "0.8.0"
    }

    object Dependencies {
        const val web3j = "4.8.9"
        const val okHttp = "4.9.3"
        const val kotlinCoroutines = "1.6.0"
        const val kotlinLogging = "2.1.21"
        const val mockitoKotlin = "4.0.0"
        const val assertk = "0.25"
        const val wireMock = "2.27.2"
        const val testContainers = "1.16.2"
        const val postgresDriver = "42.3.1"
        const val jwt = "1.0.1"
    }
}
