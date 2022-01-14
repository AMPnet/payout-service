import java.math.BigDecimal

object Configurations {

    object Compile {
        val compilerArgs = listOf("-Xjsr305=strict")
    }

    object Database {
        val url = "jdbc:postgresql://localhost:5432/postgres"
        val user = "postgres"
        val password = "postgres"
        val schema = "payout_service"
        val driverDependency = "org.postgresql:postgresql:${Versions.Dependencies.postgresDriver}"
        val driverClass = "org.postgresql.Driver"
    }

    object Docker {
        const val baseImage = "gcr.io/distroless/java"
        const val tag = "11"
        const val digest = "sha256:1d377403a44d32779be00fceec4803be0301c7f4a62b72d7307dc411860c24c3"
    }

    object Tests {
        val testSets = listOf("integTest", "apiTest")
        val minimumCoverage = BigDecimal("0.90")
    }
}
