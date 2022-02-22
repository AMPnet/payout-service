package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.blockchain.IPayoutService.PayoutStateForInvestor
import com.ampnet.payoutservice.blockchain.PayoutStruct
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.WalletAddress

data class PayoutForInvestor(
    val payout: Payout,
    val investor: WalletAddress,
    val amountClaimed: Balance
) {
    constructor(struct: PayoutStruct, state: PayoutStateForInvestor) : this(
        payout = Payout(struct),
        investor = WalletAddress(state.investor),
        amountClaimed = Balance(state.amountClaimed)
    )
}
