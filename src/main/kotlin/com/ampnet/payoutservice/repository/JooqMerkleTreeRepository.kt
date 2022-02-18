package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreePathParams
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeLeafNodeRecord
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeRootRecord
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JooqMerkleTreeRepository(private val dslContext: DSLContext, private val uuidProvider: UuidProvider) :
    MerkleTreeRepository {

    companion object : KLogging()

    override fun getById(treeId: UUID): MerkleTree? {
        logger.info { "Fetching Merkle tree, treeId: $treeId" }

        return dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
            .where(MerkleTreeRoot.MERKLE_TREE_ROOT.ID.eq(treeId))
            .fetchOne()
            ?.let { rebuildTree(it) }
    }

    override fun storeTree(
        tree: MerkleTree,
        chainId: ChainId,
        assetAddress: ContractAddress,
        blockNumber: BlockNumber
    ): UUID {
        logger.info {
            "Storing Merkle tree with root hash: ${tree.root.hash} for chainId: $chainId," +
                " assetAddress: $assetAddress, blockNumber: $blockNumber"
        }

        val rootId = uuidProvider.getUuid()

        dslContext.executeInsert(
            MerkleTreeRootRecord(
                id = rootId,
                chainId = chainId.value,
                assetAddress = assetAddress.rawValue,
                blockNumber = blockNumber.value,
                hash = tree.root.hash.value,
                hashFn = tree.hashFn.toDbEnum
            )
        )

        val insert = dslContext.insertQuery(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)

        tree.leafNodesByHash.values.forEach {
            insert.addRecord(
                MerkleTreeLeafNodeRecord(
                    id = uuidProvider.getUuid(),
                    merkleRoot = rootId,
                    address = it.value.data.address.rawValue,
                    balance = it.value.data.balance.rawValue
                )
            )
        }

        insert.execute()

        return rootId
    }

    override fun fetchTree(params: FetchMerkleTreeParams): MerkleTreeWithId? {
        logger.info { "Fetching Merkle, params: $params" }

        val root = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
            .where(
                DSL.and(
                    MerkleTreeRoot.MERKLE_TREE_ROOT.CHAIN_ID.eq(params.chainId.value),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.ASSET_ADDRESS.eq(params.assetAddress.rawValue),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.HASH.eq(params.rootHash.value)
                )
            )
            .fetchOne() ?: return null

        val tree = rebuildTree(root)

        return if (tree.root.hash == params.rootHash) {
            logger.info { "Successfully fetched and reconstructed Merkle tree, params: $params" }
            MerkleTreeWithId(root.id!!, tree)
        } else {
            logger.error { "Failed to reconstruct Merkle tree, params: $params" }
            null
        }
    }

    override fun containsAddress(params: FetchMerkleTreePathParams): Boolean {
        logger.info { "Checking if Merkle tree contains address, params: $params" }

        val root = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
            .where(
                DSL.and(
                    MerkleTreeRoot.MERKLE_TREE_ROOT.CHAIN_ID.eq(params.chainId.value),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.ASSET_ADDRESS.eq(params.assetAddress.rawValue),
                    MerkleTreeRoot.MERKLE_TREE_ROOT.HASH.eq(params.rootHash.value)
                )
            )
            .fetchOne() ?: return false

        return dslContext.fetchExists(
            dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(
                    DSL.and(
                        MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.MERKLE_ROOT.eq(root.id),
                        MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ADDRESS.eq(params.walletAddress.rawValue)
                    )
                )
        )
    }

    private fun rebuildTree(root: MerkleTreeRootRecord): MerkleTree {
        val leafNodes = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
            .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.MERKLE_ROOT.eq(root.id))
            .fetch { AccountBalance(WalletAddress(it.address!!), Balance(it.balance!!)) }
        return MerkleTree(leafNodes, HashFunction.fromDbEnum(root.hashFn!!))
    }
}
