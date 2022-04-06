package com.ampnet.payoutservice.controller.request

import java.math.BigInteger

data class CreateSnapshotRequest(
    val name: String,
    val chainId: Long,
    val assetAddress: String,
    val payoutBlockNumber: BigInteger,
    val ignoredHolderAddresses: Set<String>
)
