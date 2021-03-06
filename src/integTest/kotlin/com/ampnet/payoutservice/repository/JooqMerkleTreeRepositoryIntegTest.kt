package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeLeafNodeRecord
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeRootRecord
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreePathParams
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.testcontainers.PostgresTestContainer
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import java.math.BigInteger
import java.util.UUID
import com.ampnet.payoutservice.generated.jooq.enums.HashFunction as DbHashFunction

@JooqTest
@Import(JooqMerkleTreeRepository::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqMerkleTreeRepositoryIntegTest : TestBase() {

    @Suppress("unused")
    private val postgresContainer = PostgresTestContainer()

    @Autowired
    private lateinit var repository: MerkleTreeRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    @MockBean
    private lateinit var uuidProvider: UuidProvider

    @Test
    fun mustCorrectlyFetchAndReconstructMerkleTreeById() {
        val treeRootUuid = UUID.randomUUID()
        val leaf1Uuid = UUID.randomUUID()
        val leaf2Uuid = UUID.randomUUID()
        val leaf3Uuid = UUID.randomUUID()
        val leaf4Uuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leaf1Uuid, leaf2Uuid, leaf3Uuid, leaf4Uuid)
        }

        val leafNode1 = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val leafNode2 = AccountBalance(WalletAddress("b"), Balance(BigInteger("100")))
        val leafNode3 = AccountBalance(WalletAddress("c"), Balance(BigInteger("200")))
        val leafNode4 = AccountBalance(WalletAddress("d"), Balance(BigInteger("300")))
        val merkleTree = MerkleTree(listOf(leafNode1, leafNode2, leafNode3, leafNode4), HashFunction.IDENTITY)

        val storedTreeId = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct tree ID is returned") {
            assertThat(storedTreeId).withMessage()
                .isEqualTo(treeRootUuid)
        }

        verify("Merkle tree is correctly fetched and reconstructed") {
            val result = repository.getById(storedTreeId)

            assertThat(result).withMessage()
                .isNotNull()
            assertThat(result?.root)
                .isEqualTo(merkleTree.root)
            assertThat(result?.leafNodesByHash)
                .isEqualTo(merkleTree.leafNodesByHash)
            assertThat(result?.hashFn)
                .isEqualTo(merkleTree.hashFn)
        }
    }

    @Test
    fun mustReturnNullWhenFetchingNonExistentMerkleTreeById() {
        verify("null is returned when fetching non-existent Merkle tree") {
            val result = repository.getById(UUID.randomUUID())
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustCorrectlyStoreSimpleMerkleTreeIntoDatabase() {
        val treeRootUuid = UUID.randomUUID()
        val leafUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leafUuid)
        }

        val leafNode = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val merkleTree = MerkleTree(listOf(leafNode), HashFunction.IDENTITY)

        val chainId = ChainId(1L)
        val contractAddress = ContractAddress("b")
        val storedTreeId = suppose("simple Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                chainId,
                contractAddress,
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct tree ID is returned") {
            assertThat(storedTreeId).withMessage()
                .isEqualTo(treeRootUuid)
        }

        verify("simple Merkle tree root is correctly stored into database") {
            val rootRecord = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
                .where(MerkleTreeRoot.MERKLE_TREE_ROOT.ID.eq(treeRootUuid))
                .fetchOne()
            assertThat(rootRecord).withMessage()
                .isEqualTo(
                    MerkleTreeRootRecord(
                        id = treeRootUuid,
                        chainId = chainId.value,
                        assetAddress = contractAddress.rawValue,
                        blockNumber = BigInteger("123"),
                        hash = merkleTree.root.hash.value,
                        hashFn = DbHashFunction.IDENTITY
                    )
                )
        }

        verify("simple Merkle tree leaf is correctly stored into database") {
            val count = dslContext.selectCount()
                .from(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.MERKLE_ROOT.eq(treeRootUuid))
                .fetchOne(DSL.count())
            assertThat(count).withMessage()
                .isOne()

            val leafRecord = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leafUuid))
                .fetchOne()
            assertThat(leafRecord).withMessage()
                .isEqualTo(
                    MerkleTreeLeafNodeRecord(
                        id = leafUuid,
                        merkleRoot = treeRootUuid,
                        address = leafNode.address.rawValue,
                        balance = leafNode.balance.rawValue
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyStoreMultiNodeMerkleTreeIntoDatabase() {
        val treeRootUuid = UUID.randomUUID()
        val leaf1Uuid = UUID.randomUUID()
        val leaf2Uuid = UUID.randomUUID()
        val leaf3Uuid = UUID.randomUUID()
        val leaf4Uuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leaf1Uuid, leaf2Uuid, leaf3Uuid, leaf4Uuid)
        }

        val leafNode1 = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val leafNode2 = AccountBalance(WalletAddress("b"), Balance(BigInteger("100")))
        val leafNode3 = AccountBalance(WalletAddress("c"), Balance(BigInteger("200")))
        val leafNode4 = AccountBalance(WalletAddress("d"), Balance(BigInteger("300")))
        val merkleTree = MerkleTree(listOf(leafNode1, leafNode2, leafNode3, leafNode4), HashFunction.IDENTITY)

        val chainId = ChainId(1L)
        val contractAddress = ContractAddress("b")
        val storedTreeId = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                chainId,
                contractAddress,
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct tree ID is returned") {
            assertThat(storedTreeId).withMessage()
                .isEqualTo(treeRootUuid)
        }

        verify("multi-node Merkle tree root is correctly stored into database") {
            val rootRecord = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
                .where(MerkleTreeRoot.MERKLE_TREE_ROOT.ID.eq(treeRootUuid))
                .fetchOne()
            assertThat(rootRecord).withMessage()
                .isEqualTo(
                    MerkleTreeRootRecord(
                        id = treeRootUuid,
                        chainId = chainId.value,
                        assetAddress = contractAddress.rawValue,
                        blockNumber = BigInteger("123"),
                        hash = merkleTree.root.hash.value,
                        hashFn = DbHashFunction.IDENTITY
                    )
                )
        }

        verify("multi-node Merkle tree leaves are correctly stored into database") {
            val count = dslContext.selectCount()
                .from(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.MERKLE_ROOT.eq(treeRootUuid))
                .fetchOne(DSL.count())
            assertThat(count).withMessage()
                .isEqualTo(4)

            val leaf1Record = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leaf1Uuid))
                .fetchOne()
            assertThat(leaf1Record).withMessage()
                .isEqualTo(
                    MerkleTreeLeafNodeRecord(
                        id = leaf1Uuid,
                        merkleRoot = treeRootUuid,
                        address = leafNode1.address.rawValue,
                        balance = leafNode1.balance.rawValue
                    )
                )

            val leaf2Record = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leaf2Uuid))
                .fetchOne()
            assertThat(leaf2Record).withMessage()
                .isEqualTo(
                    MerkleTreeLeafNodeRecord(
                        id = leaf2Uuid,
                        merkleRoot = treeRootUuid,
                        address = leafNode2.address.rawValue,
                        balance = leafNode2.balance.rawValue
                    )
                )

            val leaf3Record = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leaf3Uuid))
                .fetchOne()
            assertThat(leaf3Record).withMessage()
                .isEqualTo(
                    MerkleTreeLeafNodeRecord(
                        id = leaf3Uuid,
                        merkleRoot = treeRootUuid,
                        address = leafNode3.address.rawValue,
                        balance = leafNode3.balance.rawValue
                    )
                )

            val leaf4Record = dslContext.selectFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leaf4Uuid))
                .fetchOne()
            assertThat(leaf4Record).withMessage()
                .isEqualTo(
                    MerkleTreeLeafNodeRecord(
                        id = leaf4Uuid,
                        merkleRoot = treeRootUuid,
                        address = leafNode4.address.rawValue,
                        balance = leafNode4.balance.rawValue
                    )
                )
        }
    }

    @Test
    fun mustReturnNullWhenFetchingNonExistentMerkleTreeByHash() {
        verify("null is returned when fetching non-existent Merkle tree") {
            val result = repository.fetchTree(
                FetchMerkleTreeParams(
                    Hash("a"),
                    ChainId(1L),
                    ContractAddress("1")
                )
            )
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustReturnNullWhenMerkleTreeReconstructionFailsDuringFetchByHash() {
        val treeRootUuid = UUID.randomUUID()
        val leaf1Uuid = UUID.randomUUID()
        val leaf2Uuid = UUID.randomUUID()
        val leaf3Uuid = UUID.randomUUID()
        val leaf4Uuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leaf1Uuid, leaf2Uuid, leaf3Uuid, leaf4Uuid)
        }

        val leafNode1 = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val leafNode2 = AccountBalance(WalletAddress("b"), Balance(BigInteger("100")))
        val leafNode3 = AccountBalance(WalletAddress("c"), Balance(BigInteger("200")))
        val leafNode4 = AccountBalance(WalletAddress("d"), Balance(BigInteger("300")))
        val merkleTree = MerkleTree(listOf(leafNode1, leafNode2, leafNode3, leafNode4), HashFunction.IDENTITY)

        val storedTreeId = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct tree ID is returned") {
            assertThat(storedTreeId).withMessage()
                .isEqualTo(treeRootUuid)
        }

        suppose("Merkle tree leaf node was deleted without updating root hash") {
            dslContext.deleteFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leaf4Uuid))
                .execute()
        }

        verify("null is returned when fetching Merkle tree") {
            val result = repository.fetchTree(
                FetchMerkleTreeParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b")
                )
            )
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustCorrectlyFetchAndReconstructMerkleTreeByHash() {
        val treeRootUuid = UUID.randomUUID()
        val leaf1Uuid = UUID.randomUUID()
        val leaf2Uuid = UUID.randomUUID()
        val leaf3Uuid = UUID.randomUUID()
        val leaf4Uuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leaf1Uuid, leaf2Uuid, leaf3Uuid, leaf4Uuid)
        }

        val leafNode1 = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val leafNode2 = AccountBalance(WalletAddress("b"), Balance(BigInteger("100")))
        val leafNode3 = AccountBalance(WalletAddress("c"), Balance(BigInteger("200")))
        val leafNode4 = AccountBalance(WalletAddress("d"), Balance(BigInteger("300")))
        val merkleTree = MerkleTree(listOf(leafNode1, leafNode2, leafNode3, leafNode4), HashFunction.IDENTITY)

        val storedTreeId = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct tree ID is returned") {
            assertThat(storedTreeId).withMessage()
                .isEqualTo(treeRootUuid)
        }

        verify("Merkle tree is correctly fetched and reconstructed") {
            val result = repository.fetchTree(
                FetchMerkleTreeParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b")
                )
            )
            assertThat(result).withMessage()
                .isNotNull()
            assertThat(result?.treeId)
                .isEqualTo(treeRootUuid)
            assertThat(result?.tree?.root)
                .isEqualTo(merkleTree.root)
            assertThat(result?.tree?.leafNodesByHash)
                .isEqualTo(merkleTree.leafNodesByHash)
            assertThat(result?.tree?.hashFn)
                .isEqualTo(merkleTree.hashFn)
        }
    }

    @Test
    fun mustCorrectlyCheckIfLeafNodeExists() {
        val treeRootUuid = UUID.randomUUID()
        val leaf1Uuid = UUID.randomUUID()
        val leaf2Uuid = UUID.randomUUID()
        val leaf3Uuid = UUID.randomUUID()
        val leaf4Uuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leaf1Uuid, leaf2Uuid, leaf3Uuid, leaf4Uuid)
        }

        val leafNode1 = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val leafNode2 = AccountBalance(WalletAddress("b"), Balance(BigInteger("100")))
        val leafNode3 = AccountBalance(WalletAddress("c"), Balance(BigInteger("200")))
        val leafNode4 = AccountBalance(WalletAddress("d"), Balance(BigInteger("300")))
        val merkleTree = MerkleTree(listOf(leafNode1, leafNode2, leafNode3, leafNode4), HashFunction.IDENTITY)

        val storedTreeId = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct tree ID is returned") {
            assertThat(storedTreeId).withMessage()
                .isEqualTo(treeRootUuid)
        }

        verify("multi-node Merkle tree leaves are correctly contained within the tree") {
            val leaf1Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b"),
                    leafNode1.address
                )
            )
            assertThat(leaf1Result).withMessage()
                .isTrue()

            val leaf2Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b"),
                    leafNode2.address
                )
            )
            assertThat(leaf2Result).withMessage()
                .isTrue()

            val leaf3Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b"),
                    leafNode3.address
                )
            )
            assertThat(leaf3Result).withMessage()
                .isTrue()

            val leaf4Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b"),
                    leafNode4.address
                )
            )
            assertThat(leaf4Result).withMessage()
                .isTrue()
        }

        verify("other leaves are not contained within the tree") {
            val fakeLeaf1Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(2L),
                    ContractAddress("b"),
                    leafNode1.address
                )
            )
            assertThat(fakeLeaf1Result).withMessage()
                .isFalse()

            val fakeLeaf2Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("c"),
                    leafNode1.address
                )
            )
            assertThat(fakeLeaf2Result).withMessage()
                .isFalse()

            val fakeLeaf3Result = repository.containsAddress(
                FetchMerkleTreePathParams(
                    merkleTree.root.hash,
                    ChainId(1L),
                    ContractAddress("b"),
                    WalletAddress("5")
                )
            )
            assertThat(fakeLeaf3Result).withMessage()
                .isFalse()
        }
    }
}
