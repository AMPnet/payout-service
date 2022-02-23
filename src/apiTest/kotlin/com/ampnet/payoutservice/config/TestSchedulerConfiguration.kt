package com.ampnet.payoutservice.config

import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.service.CreatePayoutQueueServiceImpl
import com.ampnet.payoutservice.service.ScheduledExecutorServiceProvider
import mu.KLogging
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestSchedulerConfiguration {

    companion object : KLogging()

    @Bean
    fun createPayoutTaskQueueScheduler() = ManualFixedScheduler()

    @Bean
    @Primary
    fun scheduledExecutorServiceProvider(
        createPayoutTaskQueueScheduler: ManualFixedScheduler
    ): ScheduledExecutorServiceProvider {
        logger.info { "Using manual schedulers for tests" }
        return mock {
            given(it.newSingleThreadScheduledExecutor(CreatePayoutQueueServiceImpl.QUEUE_NAME))
                .willReturn(createPayoutTaskQueueScheduler)
        }
    }
}
