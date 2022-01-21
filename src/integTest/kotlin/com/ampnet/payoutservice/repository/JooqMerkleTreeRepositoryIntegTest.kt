package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeLeafNodeRecord
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeRootRecord
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
    fun mustCorrectlyStoreSimpleMerkleTreeIntoDatabase() {
        val treeRootUuid = UUID.randomUUID()
        val leafUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(treeRootUuid, leafUuid)
        }

        val leafNode = AccountBalance(WalletAddress("a"), Balance(BigInteger.ZERO))
        val merkleTree = MerkleTree(listOf(leafNode), HashFunction.IDENTITY)

        val storedRootHash = suppose("simple Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct root hash is returned") {
            assertThat(storedRootHash).withMessage()
                .isEqualTo(merkleTree.root.hash)
        }

        verify("simple Merkle tree root is correctly stored into database") {
            val rootRecord = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
                .where(MerkleTreeRoot.MERKLE_TREE_ROOT.ID.eq(treeRootUuid))
                .fetchOne()
            assertThat(rootRecord).withMessage()
                .isEqualTo(
                    MerkleTreeRootRecord(
                        id = treeRootUuid,
                        chainId = 1L,
                        contractAddress = ContractAddress("b").rawValue,
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

        val storedRootHash = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct root hash is returned") {
            assertThat(storedRootHash).withMessage()
                .isEqualTo(merkleTree.root.hash)
        }

        verify("multi-node Merkle tree root is correctly stored into database") {
            val rootRecord = dslContext.selectFrom(MerkleTreeRoot.MERKLE_TREE_ROOT)
                .where(MerkleTreeRoot.MERKLE_TREE_ROOT.ID.eq(treeRootUuid))
                .fetchOne()
            assertThat(rootRecord).withMessage()
                .isEqualTo(
                    MerkleTreeRootRecord(
                        id = treeRootUuid,
                        chainId = 1L,
                        contractAddress = ContractAddress("b").rawValue,
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
    fun mustReturnNullWhenFetchingNonExistentMerkleTree() {
        verify("null is returned when fetching non-existent Merkle tree") {
            val result = repository.fetchTree(Hash("a"), ChainId(1L), ContractAddress("1"), BlockNumber(BigInteger.TEN))
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustReturnNullWhenMerkleTreeReconstructionFailsDuringFetch() {
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

        val storedRootHash = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct root hash is returned") {
            assertThat(storedRootHash).withMessage()
                .isEqualTo(merkleTree.root.hash)
        }

        suppose("Merkle tree leaf node was deleted without updating root hash") {
            dslContext.deleteFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE)
                .where(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE.ID.eq(leaf4Uuid))
                .execute()
        }

        verify("null is returned when fetching Merkle tree") {
            val result = repository.fetchTree(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustReturnCorrectlyFetchAndReconstructMerkleTree() {
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

        val storedRootHash = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct root hash is returned") {
            assertThat(storedRootHash).withMessage()
                .isEqualTo(merkleTree.root.hash)
        }

        verify("Merkle tree is correctly fetched and reconstructed") {
            val result = repository.fetchTree(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
            assertThat(result).withMessage()
                .isNotNull()
            assertThat(result?.root)
                .isEqualTo(merkleTree.root)
            assertThat(result?.leafNodes)
                .isEqualTo(merkleTree.leafNodes)
            assertThat(result?.hashFn)
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

        val storedRootHash = suppose("multi-node Merkle tree is stored into database") {
            repository.storeTree(
                merkleTree,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123"))
            )
        }

        verify("correct root hash is returned") {
            assertThat(storedRootHash).withMessage()
                .isEqualTo(merkleTree.root.hash)
        }

        verify("multi-node Merkle tree leaves are correctly contained within the tree") {
            val leaf1Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123")),
                leafNode1
            )
            assertThat(leaf1Result).withMessage()
                .isTrue()

            val leaf2Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123")),
                leafNode2
            )
            assertThat(leaf2Result).withMessage()
                .isTrue()

            val leaf3Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123")),
                leafNode3
            )
            assertThat(leaf3Result).withMessage()
                .isTrue()

            val leaf4Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123")),
                leafNode4
            )
            assertThat(leaf4Result).withMessage()
                .isTrue()
        }

        verify("other leaves are not contained within the tree") {
            val fakeLeaf1Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(2L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123")),
                leafNode1
            )
            assertThat(fakeLeaf1Result).withMessage()
                .isFalse()

            val fakeLeaf2Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("c"),
                BlockNumber(BigInteger("123")),
                leafNode1
            )
            assertThat(fakeLeaf2Result).withMessage()
                .isFalse()

            val fakeLeaf3Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("124")),
                leafNode1
            )
            assertThat(fakeLeaf3Result).withMessage()
                .isFalse()

            val fakeLeaf4Result = repository.containsLeaf(
                merkleTree.root.hash,
                ChainId(1L),
                ContractAddress("b"),
                BlockNumber(BigInteger("123")),
                AccountBalance(WalletAddress("5"), Balance(BigInteger("400")))
            )
            assertThat(fakeLeaf4Result).withMessage()
                .isFalse()
        }
    }
}
