package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.model.params.GetPayoutsForAdminParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.model.result.Payout
import com.ampnet.payoutservice.model.result.PayoutForInvestor
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress

interface BlockchainService {
    fun fetchErc20AccountBalances(
        chainId: ChainId,
        erc20ContractAddress: ContractAddress,
        ignoredErc20Addresses: Set<WalletAddress>,
        startBlock: BlockNumber?,
        endBlock: BlockNumber
    ): List<AccountBalance>

    fun getAssetOwner(chainId: ChainId, assetAddress: ContractAddress): WalletAddress

    fun getPayoutsForAdmin(params: GetPayoutsForAdminParams): List<Payout>

    fun getPayoutsForInvestor(params: GetPayoutsForInvestorParams): List<PayoutForInvestor>

    fun findContractDeploymentBlockNumber(chainId: ChainId, contractAddress: ContractAddress): BlockNumber
}
