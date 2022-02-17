package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.generated.jooq.enums.TaskStatus as DbTaskStatus

enum class TaskStatus(val toDbEnum: DbTaskStatus) {
    PENDING(DbTaskStatus.PENDING),
    SUCCESS(DbTaskStatus.SUCCESS),
    FAILED(DbTaskStatus.FAILED);

    companion object {
        fun fromDbEnum(value: DbTaskStatus): TaskStatus {
            return values().find { it.toDbEnum == value }
                ?: throw IllegalStateException("DB enum not added to code: $value")
        }
    }
}
