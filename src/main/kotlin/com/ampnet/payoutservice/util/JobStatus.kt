package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.generated.jooq.enums.JobStatus as DbJobStatus

enum class JobStatus(val toDbEnum: DbJobStatus) {
    PENDING(DbJobStatus.PENDING),
    SUCCESS(DbJobStatus.SUCCESS),
    FAILED(DbJobStatus.FAILED);

    companion object {
        fun fromDbEnum(value: DbJobStatus): JobStatus {
            return values().find { it.toDbEnum == value }
                ?: throw IllegalStateException("DB enum not added to code: $value")
        }
    }
}
