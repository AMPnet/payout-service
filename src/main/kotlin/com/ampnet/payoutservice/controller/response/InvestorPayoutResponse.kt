package com.ampnet.payoutservice.controller.response

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger

data class InvestorPayoutResponse(
    val payout: PayoutResponse,
    val investor: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val amountClaimed: BigInteger,

    @JsonSerialize(using = ToStringSerializer::class)
    val amountClaimable: BigInteger?,
    @JsonSerialize(using = ToStringSerializer::class)
    val balance: BigInteger?,
    val proof: List<String>?
)
