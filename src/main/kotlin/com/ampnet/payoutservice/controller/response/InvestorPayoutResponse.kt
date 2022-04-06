package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger

data class InvestorPayoutResponse(
    val payout: PayoutResponse,
    val investor: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val amountClaimed: BigInteger,

    @JsonSerialize(using = ToStringSerializer::class)
    val amountClaimable: BigInteger,
    @JsonSerialize(using = ToStringSerializer::class)
    val balance: BigInteger,
    val proof: List<String>
) {
    companion object {
        @Suppress("LongParameterList")
        operator fun invoke(
            payout: PayoutResponse,
            investor: String,
            amountClaimed: BigInteger,
            amountClaimable: BigInteger,
            balance: BigInteger,
            path: List<PathSegment>
        ) = InvestorPayoutResponse(
            payout = payout,
            investor = investor,
            amountClaimed = amountClaimed,
            amountClaimable = amountClaimable,
            balance = balance,
            proof = path.map { it.siblingHash.value }
        )
    }
}
