package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeRootRecord
import com.ampnet.payoutservice.generated.jooq.tables.records.SnapshotRecord
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.result.OtherSnapshotData
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.model.result.SuccessfulSnapshotData
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.testcontainers.PostgresTestContainer
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotFailureCause
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import java.math.BigInteger
import java.util.UUID
import com.ampnet.payoutservice.generated.jooq.enums.SnapshotStatus as DbSnapshotStatus
import com.ampnet.payoutservice.generated.jooq.tables.Snapshot as SnapshotTable

@JooqTest
@Import(JooqSnapshotRepository::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqSnapshotRepositoryIntegTest : TestBase() {

    @Suppress("unused")
    private val postgresContainer = PostgresTestContainer()

    @Autowired
    private lateinit var repository: SnapshotRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    @MockBean
    private lateinit var uuidProvider: UuidProvider

    @Test
    fun mustCorrectlyFetchSuccessfulSnapshotId() {
        val treeUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val treeRootHash = Hash("tree-root-hash")
        val hashFn = HashFunction.IDENTITY

        suppose("some Merkle tree root exists in database") {
            dslContext.executeInsert(
                MerkleTreeRootRecord(
                    id = treeUuid,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    hash = treeRootHash.value,
                    hashFn = hashFn.toDbEnum
                )
            )
        }

        val snapshotUuid = UUID.randomUUID()
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))
        val treeIpfsHash = IpfsHash("tree-ipfs-hash")
        val totalAssetAmount = Balance(BigInteger("123"))

        suppose("successful snapshot is stored into database") {
            dslContext.executeInsert(
                SnapshotRecord(
                    id = snapshotUuid,
                    name = name,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredHolderAddresses = ignoredHolderAddresses.map { it.rawValue }.toTypedArray(),
                    ownerAddress = ownerAddress.rawValue,
                    status = SnapshotStatus.SUCCESS.toDbEnum,
                    failureCause = null,
                    resultTree = treeUuid,
                    treeIpfsHash = treeIpfsHash.value,
                    totalAssetAmount = totalAssetAmount.rawValue
                )
            )
        }

        verify("successful snapshot is correctly fetched from database") {
            val result = repository.getById(snapshotUuid)

            assertThat(result).withMessage()
                .isEqualTo(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = SuccessfulSnapshotData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = treeIpfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchPendingSnapshotById() {
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))

        suppose("pending snapshot is stored into database") {
            dslContext.executeInsert(
                SnapshotRecord(
                    id = snapshotUuid,
                    name = name,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredHolderAddresses = ignoredHolderAddresses.map { it.rawValue }.toTypedArray(),
                    ownerAddress = ownerAddress.rawValue,
                    status = SnapshotStatus.PENDING.toDbEnum,
                    failureCause = null,
                    resultTree = null,
                    treeIpfsHash = null,
                    totalAssetAmount = null
                )
            )
        }

        verify("pending snapshot is correctly fetched from database") {
            val result = repository.getById(snapshotUuid)

            assertThat(result).withMessage()
                .isEqualTo(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = OtherSnapshotData(SnapshotStatus.PENDING, null)
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchFailedSnapshotById() {
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))

        suppose("failed snapshot is stored into database") {
            dslContext.executeInsert(
                SnapshotRecord(
                    id = snapshotUuid,
                    name = name,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredHolderAddresses = ignoredHolderAddresses.map { it.rawValue }.toTypedArray(),
                    ownerAddress = ownerAddress.rawValue,
                    status = SnapshotStatus.FAILED.toDbEnum,
                    failureCause = SnapshotFailureCause.OTHER.toDbEnum,
                    resultTree = null,
                    treeIpfsHash = null,
                    totalAssetAmount = null
                )
            )
        }

        verify("failed snapshot is correctly fetched from database") {
            val result = repository.getById(snapshotUuid)

            assertThat(result).withMessage()
                .isEqualTo(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = OtherSnapshotData(SnapshotStatus.FAILED, SnapshotFailureCause.OTHER)
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchAllSnapshotsByChainIdOwnerAndStatuses() {
        val owner1 = WalletAddress("aaa1")
        val chainId1 = ChainId(1L)
        val owner1chainId1Snapshots = listOf(
            snapshotRecord(chainId1, owner1, SnapshotStatus.PENDING, null),
            snapshotRecord(chainId1, owner1, SnapshotStatus.FAILED, SnapshotFailureCause.OTHER)
        )

        val chainId2 = ChainId(2L)
        val owner1chainId2Snapshots = listOf(
            snapshotRecord(chainId2, owner1, SnapshotStatus.PENDING, null),
            snapshotRecord(chainId2, owner1, SnapshotStatus.PENDING, null),
            snapshotRecord(chainId2, owner1, SnapshotStatus.FAILED, SnapshotFailureCause.OTHER),
            snapshotRecord(chainId2, owner1, SnapshotStatus.FAILED, SnapshotFailureCause.OTHER)
        )

        val owner2 = WalletAddress("aaa2")
        val owner2chainId1Snapshots = listOf(
            snapshotRecord(chainId1, owner2, SnapshotStatus.PENDING, null),
            snapshotRecord(chainId1, owner2, SnapshotStatus.PENDING, null),
            snapshotRecord(chainId1, owner2, SnapshotStatus.PENDING, null)
        )

        val owner2chainId2Snapshots = listOf(
            snapshotRecord(chainId2, owner2, SnapshotStatus.PENDING, null)
        )

        val owner1Snapshots = owner1chainId1Snapshots + owner1chainId2Snapshots
        val owner2Snapshots = owner2chainId1Snapshots + owner2chainId2Snapshots
        val allSnapshots = owner1Snapshots + owner2Snapshots

        suppose("all snapshots are stored into database") {
            dslContext.batchInsert(allSnapshots).execute()
        }

        verify("snapshots are correctly fetched by owner and chainId") {
            assertThat(repository.getAllByChainIdOwnerAndStatuses(chainId1, owner1, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(owner1chainId1Snapshots.toModels())
            assertThat(repository.getAllByChainIdOwnerAndStatuses(chainId2, owner1, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(owner1chainId2Snapshots.toModels())
            assertThat(repository.getAllByChainIdOwnerAndStatuses(chainId1, owner2, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(owner2chainId1Snapshots.toModels())
            assertThat(repository.getAllByChainIdOwnerAndStatuses(chainId2, owner2, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(owner2chainId2Snapshots.toModels())
        }

        val chainId1Snapshots = owner1chainId1Snapshots + owner2chainId1Snapshots
        val chainId2Snapshots = owner1chainId2Snapshots + owner2chainId2Snapshots

        verify("snapshots are correctly fetched by chainId") {
            assertThat(repository.getAllByChainIdOwnerAndStatuses(chainId1, null, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(chainId1Snapshots.toModels())
            assertThat(repository.getAllByChainIdOwnerAndStatuses(chainId2, null, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(chainId2Snapshots.toModels())
        }

        verify("snapshots are correctly fetched by owner") {
            assertThat(repository.getAllByChainIdOwnerAndStatuses(null, owner1, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(owner1Snapshots.toModels())
            assertThat(repository.getAllByChainIdOwnerAndStatuses(null, owner2, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(owner2Snapshots.toModels())
        }

        verify("snapshots are correctly fetched by status") {
            assertThat(
                repository.getAllByChainIdOwnerAndStatuses(null, null, setOf(SnapshotStatus.PENDING))
            )
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    allSnapshots.filter { it.status == DbSnapshotStatus.PENDING }.toModels()
                )

            assertThat(
                repository.getAllByChainIdOwnerAndStatuses(null, null, setOf(SnapshotStatus.FAILED))
            )
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    allSnapshots.filter { it.status == DbSnapshotStatus.FAILED }.toModels()
                )
        }

        verify("snapshots are correctly fetched with null filters") {
            assertThat(repository.getAllByChainIdOwnerAndStatuses(null, null, emptySet()))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(allSnapshots.toModels())
        }
    }

    @Test
    fun mustReturnNullWhenFetchingNonExistentSnapshotById() {
        verify("null is returned when fetching non-existent snapshot") {
            val result = repository.getById(UUID.randomUUID())
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustCorrectlyCreateSnapshot() {
        val snapshotUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(snapshotUuid)
        }

        val chainId = ChainId(1L)
        val name = "snapshot-name"
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))

        val storedSnapshotId = suppose("snapshot is stored into database") {
            repository.createSnapshot(
                CreateSnapshotParams(
                    chainId = chainId,
                    name = name,
                    assetAddress = assetAddress,
                    ownerAddress = ownerAddress,
                    payoutBlock = payoutBlock,
                    ignoredHolderAddresses = ignoredHolderAddresses
                )
            )
        }

        verify("correct snapshot ID is returned") {
            assertThat(storedSnapshotId).withMessage()
                .isEqualTo(snapshotUuid)
        }

        verify("snapshot is correctly stored into database") {
            val record = dslContext.selectFrom(SnapshotTable.SNAPSHOT)
                .where(SnapshotTable.SNAPSHOT.ID.eq(snapshotUuid))
                .fetchOne()
            assertThat(record).withMessage()
                .isEqualTo(
                    SnapshotRecord(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId.value,
                        assetAddress = assetAddress.rawValue,
                        blockNumber = payoutBlock.value,
                        ignoredHolderAddresses = ignoredHolderAddresses.map { it.rawValue }.toTypedArray(),
                        ownerAddress = ownerAddress.rawValue,
                        status = SnapshotStatus.PENDING.toDbEnum,
                        failureCause = null,
                        resultTree = null,
                        treeIpfsHash = null,
                        totalAssetAmount = null
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchSinglePendingSnapshotFromDatabase() {
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))

        suppose("pending snapshot is stored into database") {
            dslContext.executeInsert(
                SnapshotRecord(
                    id = snapshotUuid,
                    name = name,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredHolderAddresses = ignoredHolderAddresses.map { it.rawValue }.toTypedArray(),
                    ownerAddress = ownerAddress.rawValue,
                    status = SnapshotStatus.PENDING.toDbEnum,
                    failureCause = null,
                    resultTree = null,
                    treeIpfsHash = null,
                    totalAssetAmount = null
                )
            )
        }

        verify("single pending snapshot is fetched from database") {
            val result = repository.getPending()

            assertThat(result).withMessage()
                .isEqualTo(
                    PendingSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress
                    )
                )
        }
    }

    @Test
    fun mustReturnNullWhenThereAreNoPendingSnapshots() {
        verify("null is returned when fetching single pending snapshot") {
            val result = repository.getPending()
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustCorrectlyCompleteSnapshot() {
        val snapshotUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(snapshotUuid)
        }

        val name = "snapshot-name"
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))

        val storedSnapshotId = suppose("snapshot is stored into database") {
            repository.createSnapshot(
                CreateSnapshotParams(
                    name = name,
                    chainId = chainId,
                    assetAddress = assetAddress,
                    ownerAddress = ownerAddress,
                    payoutBlock = payoutBlock,
                    ignoredHolderAddresses = ignoredHolderAddresses
                )
            )
        }

        verify("correct snapshot ID is returned") {
            assertThat(storedSnapshotId).withMessage()
                .isEqualTo(snapshotUuid)
        }

        val treeUuid = UUID.randomUUID()
        val treeRootHash = Hash("tree-root-hash")
        val hashFn = HashFunction.IDENTITY

        suppose("some Merkle tree root exists in database") {
            dslContext.executeInsert(
                MerkleTreeRootRecord(
                    id = treeUuid,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    hash = treeRootHash.value,
                    hashFn = hashFn.toDbEnum
                )
            )
        }

        val treeIpfsHash = IpfsHash("tree-ipfs-hash")
        val totalAssetAmount = Balance(BigInteger("123"))
        val expectedResult = Snapshot(
            id = snapshotUuid,
            name = name,
            chainId = chainId,
            assetAddress = assetAddress,
            blockNumber = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses,
            ownerAddress = ownerAddress,
            data = SuccessfulSnapshotData(
                merkleTreeRootId = treeUuid,
                merkleTreeIpfsHash = treeIpfsHash,
                totalAssetAmount = totalAssetAmount
            )
        )

        verify("snapshot is completed") {
            val result = repository.completeSnapshot(snapshotUuid, treeUuid, treeIpfsHash, totalAssetAmount)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }

        verify("successful snapshot is correctly fetched from database") {
            val result = repository.getById(snapshotUuid)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }
    }

    @Test
    fun mustCorrectlyFailSnapshot() {
        val snapshotUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(snapshotUuid)
        }

        val name = "snapshot-name"
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ownerAddress = WalletAddress("b")
        val ignoredHolderAddresses = setOf(WalletAddress("e"))

        val storedSnapshotId = suppose("snapshot is stored into database") {
            repository.createSnapshot(
                CreateSnapshotParams(
                    name = name,
                    chainId = chainId,
                    assetAddress = assetAddress,
                    ownerAddress = ownerAddress,
                    payoutBlock = payoutBlock,
                    ignoredHolderAddresses = ignoredHolderAddresses
                )
            )
        }

        verify("correct snapshot ID is returned") {
            assertThat(storedSnapshotId).withMessage()
                .isEqualTo(snapshotUuid)
        }

        val expectedResult = Snapshot(
            id = snapshotUuid,
            name = name,
            chainId = chainId,
            assetAddress = assetAddress,
            blockNumber = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses,
            ownerAddress = ownerAddress,
            data = OtherSnapshotData(SnapshotStatus.FAILED, SnapshotFailureCause.OTHER)
        )

        verify("snapshot failed") {
            val result = repository.failSnapshot(snapshotUuid, SnapshotFailureCause.OTHER)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }

        verify("failed snapshot is correctly fetched from database") {
            val result = repository.getById(snapshotUuid)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }
    }

    private fun snapshotRecord(
        chainId: ChainId,
        owner: WalletAddress,
        status: SnapshotStatus,
        failureCause: SnapshotFailureCause?
    ): SnapshotRecord {
        val id = UUID.randomUUID()
        return SnapshotRecord(
            id = id,
            name = "snapshot-$id",
            chainId = chainId.value,
            assetAddress = ContractAddress("a").rawValue,
            blockNumber = BlockNumber(BigInteger.TEN).value,
            ignoredHolderAddresses = emptyArray(),
            ownerAddress = owner.rawValue,
            status = status.toDbEnum,
            failureCause = failureCause?.toDbEnum,
            resultTree = null,
            treeIpfsHash = null,
            totalAssetAmount = null
        )
    }

    private fun List<SnapshotRecord>.toModels(): List<Snapshot> =
        map {
            Snapshot(
                id = it.id!!,
                name = it.name!!,
                chainId = ChainId(it.chainId!!),
                assetAddress = ContractAddress(it.assetAddress!!),
                blockNumber = BlockNumber(it.blockNumber!!),
                ignoredHolderAddresses = emptySet(),
                ownerAddress = WalletAddress(it.ownerAddress!!),
                data = OtherSnapshotData(
                    status = SnapshotStatus.fromDbEnum(it.status!!),
                    failureCause = it.failureCause?.let(SnapshotFailureCause::fromDbEnum)
                )
            )
        }
}
