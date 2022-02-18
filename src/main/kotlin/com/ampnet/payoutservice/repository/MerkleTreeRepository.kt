package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreePathParams
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.MerkleTree
import java.util.UUID

interface MerkleTreeRepository {
    fun getById(treeId: UUID): MerkleTree?
    fun storeTree(tree: MerkleTree, chainId: ChainId, assetAddress: ContractAddress, blockNumber: BlockNumber): UUID
    fun fetchTree(params: FetchMerkleTreeParams): MerkleTreeWithId?
    fun containsAddress(params: FetchMerkleTreePathParams): Boolean
}
