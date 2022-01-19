package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress

interface BlockchainService {
    fun fetchErc20AccountBalances(
        chainId: ChainId,
        erc20ContractAddress: ContractAddress,
        startBlock: BlockNumber?,
        endBlock: BlockNumber
    ): List<AccountBalance>
}
