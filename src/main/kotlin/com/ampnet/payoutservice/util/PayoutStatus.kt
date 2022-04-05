package com.ampnet.payoutservice.util

@Deprecated("for removal in SD-709")
enum class PayoutStatus(val toSnapshotStatus: SnapshotStatus) {
    PROOF_PENDING(SnapshotStatus.PENDING),
    PROOF_FAILED(SnapshotStatus.FAILED),
    PROOF_CREATED(SnapshotStatus.SUCCESS),
    PAYOUT_CREATED(SnapshotStatus.SUCCESS);

    companion object {
        fun fromTaskStatus(value: SnapshotStatus): PayoutStatus {
            return when (value) {
                SnapshotStatus.PENDING -> PROOF_PENDING
                SnapshotStatus.SUCCESS -> PROOF_CREATED
                SnapshotStatus.FAILED -> PROOF_FAILED
            }
        }
    }
}
