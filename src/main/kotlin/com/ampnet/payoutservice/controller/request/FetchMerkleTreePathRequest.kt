package com.ampnet.payoutservice.controller.request

import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.WalletAddress
import java.math.BigInteger

data class RawFetchMerkleTreePathRequest(
    val rootHash: String,
    val chainId: Long,
    val contractAddress: String,
    val blockNumber: BigInteger,
    val walletAddress: String,
    val walletBalance: BigInteger
) {
    fun stronglyTyped() = FetchMerkleTreePathRequest(
        Hash(rootHash),
        ChainId(chainId),
        ContractAddress(contractAddress),
        BlockNumber(blockNumber),
        AccountBalance(
            WalletAddress(walletAddress),
            Balance(walletBalance)
        )
    )
}

data class FetchMerkleTreePathRequest(
    val rootHash: Hash,
    val chainId: ChainId,
    val contractAddress: ContractAddress,
    val blockNumber: BlockNumber,
    val leaf: AccountBalance
)
