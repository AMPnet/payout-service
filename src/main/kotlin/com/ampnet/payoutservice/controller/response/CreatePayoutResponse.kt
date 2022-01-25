package com.ampnet.payoutservice.controller.response

import java.math.BigInteger

data class CreatePayoutResponse(
    val payoutBlockNumber: BigInteger,
    val merkleRootHash: String
)
