package com.ampnet.payoutservice.config

import org.jooq.ConnectionProvider
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource
import org.jooq.Configuration as JooqConfiguration

@Configuration
@EnableTransactionManagement
class DatabaseConfig {

    @Bean
    fun dataSource(properties: DataSourceProperties): DataSource {
        return DataSourceBuilder.create()
            .url(properties.url)
            .username(properties.username)
            .password(properties.password)
            .driverClassName(properties.driverClassName)
            .build()
    }

    @Bean
    fun connectionProvider(dataSource: DataSource): ConnectionProvider {
        return DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource))
    }

    @Bean
    fun configuration(connectionProvider: ConnectionProvider): JooqConfiguration {
        return DefaultConfiguration().apply {
            set(connectionProvider)
            set(SQLDialect.POSTGRES)
        }
    }

    @Bean
    fun dslContext(configuration: JooqConfiguration): DSLContext {
        return DSL.using(configuration)
    }
}
