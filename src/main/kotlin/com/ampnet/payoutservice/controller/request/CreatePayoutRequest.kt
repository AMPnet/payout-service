package com.ampnet.payoutservice.controller.request

import java.math.BigInteger

data class CreatePayoutRequest(
    val payoutBlockNumber: BigInteger,
    val ignoredHolderAddresses: Set<String>,
    val issuerAddress: String?
)
