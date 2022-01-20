package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeLeafNodeRecord
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class MerkleTreeRepository(private val dslContext: DSLContext, private val uuidProvider: UuidProvider) {

    companion object : KLogging()

    fun storeTree(tree: MerkleTree, chainId: ChainId, contractAddress: ContractAddress, blockNumber: BlockNumber) {
        logger.info {
            "Storing Merkle tree with root hash: ${tree.root.hash} for chainId: $chainId," +
                " contractAddress: $contractAddress, blockNumber: $blockNumber"
        }

        val rootId = uuidProvider.getUuid()

        dslContext.insertInto(MerkleTreeRoot.MERKLE_TREE_ROOT)
            .set(MerkleTreeRoot.MERKLE_TREE_ROOT.ID, rootId)
            .set(MerkleTreeRoot.MERKLE_TREE_ROOT.CHAIN_ID, chainId.value)
            .set(MerkleTreeRoot.MERKLE_TREE_ROOT.CONTRACT_ADDRESS, contractAddress.rawValue)
            .set(MerkleTreeRoot.MERKLE_TREE_ROOT.BLOCK_NUMBER, blockNumber.value)
            .set(MerkleTreeRoot.MERKLE_TREE_ROOT.HASH, tree.root.hash.value)
            .set(MerkleTreeRoot.MERKLE_TREE_ROOT.HASH_FN, tree.hashFn.toDbEnum)
            .execute()

        val leafNodes = tree.leafNodes.values.map {
            MerkleTreeLeafNodeRecord(
                id = uuidProvider.getUuid(),
                merkleRoot = rootId,
                address = it.data.address.rawValue,
                balance = it.data.balance.rawValue
            )
        }

        dslContext.insertInto(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
            .values(leafNodes)
            .execute()
    }

    fun fetchTree(
        rootHash: Hash,
        chainId: ChainId,
        contractAddress: ContractAddress,
        blockNumber: BlockNumber
    ): MerkleTree? {
        logger.info {
            "Fetching Merkle tree with root hash: ${rootHash} for chainId: $chainId," +
                " contractAddress: $contractAddress, blockNumber: $blockNumber"
        }

        val root = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
            .where(
                DSL.and(
                    MerkleTreeRoot.MERKLE_TREE_ROOT.CHAIN_ID.eq(chainId.value),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.CONTRACT_ADDRESS.eq(contractAddress.rawValue),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.BLOCK_NUMBER.eq(blockNumber.value),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.HASH.eq(rootHash.value)
                )
            )
            .fetchOne() ?: return null

        val leafNodes = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
            .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.MERKLE_ROOT.eq(root.id))
            .fetch { AccountBalance(WalletAddress(it.address!!), Balance(it.balance!!)) }
        val tree = MerkleTree(leafNodes, HashFunction.fromDbEnum(root.hashFn!!))

        return if (tree.root.hash == rootHash) {
            logger.info {
                "Successfully fetched and reconstructed Merkle tree with root hash: ${rootHash} for" +
                    " chainId: $chainId, contractAddress: $contractAddress, blockNumber: $blockNumber"
            }
            tree
        } else {
            logger.error {
                "Failed to reconstruct Merkle tree with root hash: ${rootHash} for" +
                    " chainId: $chainId, contractAddress: $contractAddress, blockNumber: $blockNumber"
            }
            null
        }
    }
}
