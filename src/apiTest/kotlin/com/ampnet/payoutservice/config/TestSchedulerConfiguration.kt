package com.ampnet.payoutservice.config

import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.service.ScheduledExecutorServiceProvider
import com.ampnet.payoutservice.service.SnapshotQueueServiceImpl
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
    fun snapshotQueueScheduler() = ManualFixedScheduler()

    @Bean
    @Primary
    fun scheduledExecutorServiceProvider(
        snapshotQueueScheduler: ManualFixedScheduler
    ): ScheduledExecutorServiceProvider {
        logger.info { "Using manual schedulers for tests" }
        return mock {
            given(it.newSingleThreadScheduledExecutor(SnapshotQueueServiceImpl.QUEUE_NAME))
                .willReturn(snapshotQueueScheduler)
        }
    }
}
