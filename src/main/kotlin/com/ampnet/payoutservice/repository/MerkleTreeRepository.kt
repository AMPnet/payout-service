package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.MerkleTree

interface MerkleTreeRepository {

    fun storeTree(tree: MerkleTree, chainId: ChainId, contractAddress: ContractAddress, blockNumber: BlockNumber)
    fun fetchTree(
        rootHash: Hash,
        chainId: ChainId,
        contractAddress: ContractAddress,
        blockNumber: BlockNumber
    ): MerkleTree?

    fun containsLeaf(
        rootHash: Hash,
        chainId: ChainId,
        contractAddress: ContractAddress,
        blockNumber: BlockNumber,
        leaf: AccountBalance
    ): Boolean
}
