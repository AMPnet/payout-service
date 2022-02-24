package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.generated.jooq.enums.TaskStatus as DbTaskStatus

enum class TaskStatus(val toDbEnum: DbTaskStatus, val toPayoutStatus: PayoutStatus) {
    PENDING(DbTaskStatus.PENDING, PayoutStatus.PROOF_PENDING),
    SUCCESS(DbTaskStatus.SUCCESS, PayoutStatus.PROOF_CREATED),
    FAILED(DbTaskStatus.FAILED, PayoutStatus.PROOF_FAILED);

    companion object {
        fun fromDbEnum(value: DbTaskStatus): TaskStatus {
            return values().find { it.toDbEnum == value }
                ?: throw IllegalStateException("DB enum not added to code: $value")
        }
    }
}
