package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.WalletAddress

data class PayoutForInvestor(
    val payout: Payout,
    val investor: WalletAddress,
    val amountClaimed: Balance
)
