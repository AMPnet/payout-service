package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.generated.jooq.tables.records.CreatePayoutTaskRecord
import com.ampnet.payoutservice.generated.jooq.tables.records.MerkleTreeRootRecord
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.model.result.CreatePayoutTask
import com.ampnet.payoutservice.model.result.OtherTaskData
import com.ampnet.payoutservice.model.result.PendingCreatePayoutTask
import com.ampnet.payoutservice.model.result.SuccessfulTaskData
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.testcontainers.PostgresTestContainer
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.TaskStatus
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
import com.ampnet.payoutservice.generated.jooq.tables.CreatePayoutTask as CreatePayoutTaskTable

@JooqTest
@Import(JooqCreatePayoutTaskRepository::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqCreatePayoutTaskRepositoryIntegTest : TestBase() {

    @Suppress("unused")
    private val postgresContainer = PostgresTestContainer()

    @Autowired
    private lateinit var repository: CreatePayoutTaskRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    @MockBean
    private lateinit var uuidProvider: UuidProvider

    @Test
    fun mustCorrectlyFetchSuccessfulCreatePayoutTaskById() {
        val treeUuid = UUID.randomUUID()
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

        val taskUuid = UUID.randomUUID()
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))
        val treeIpfsHash = IpfsHash("tree-ipfs-hash")
        val totalAssetAmount = BigInteger("123")

        suppose("successful task is stored into database") {
            dslContext.executeInsert(
                CreatePayoutTaskRecord(
                    id = taskUuid,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredAssetAddresses = ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                    requesterAddress = requesterAddress.rawValue,
                    issuerAddress = issuerAddress.rawValue,
                    status = TaskStatus.SUCCESS.toDbEnum,
                    resultTree = treeUuid,
                    treeIpfsHash = treeIpfsHash.value,
                    totalAssetAmount = totalAssetAmount
                )
            )
        }

        verify("successful create payout task is correctly fetched from database") {
            val result = repository.getById(taskUuid)

            assertThat(result).withMessage()
                .isEqualTo(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAssetAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = SuccessfulTaskData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = treeIpfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchPendingCreatePayoutTaskById() {
        val taskUuid = UUID.randomUUID()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))

        suppose("pending task is stored into database") {
            dslContext.executeInsert(
                CreatePayoutTaskRecord(
                    id = taskUuid,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredAssetAddresses = ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                    requesterAddress = requesterAddress.rawValue,
                    issuerAddress = issuerAddress.rawValue,
                    status = TaskStatus.PENDING.toDbEnum,
                    resultTree = null,
                    treeIpfsHash = null,
                    totalAssetAmount = null
                )
            )
        }

        verify("pending create payout task is correctly fetched from database") {
            val result = repository.getById(taskUuid)

            assertThat(result).withMessage()
                .isEqualTo(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAssetAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = OtherTaskData(TaskStatus.PENDING)
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchFailedCreatePayoutTaskById() {
        val taskUuid = UUID.randomUUID()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))

        suppose("failed task is stored into database") {
            dslContext.executeInsert(
                CreatePayoutTaskRecord(
                    id = taskUuid,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredAssetAddresses = ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                    requesterAddress = requesterAddress.rawValue,
                    issuerAddress = issuerAddress.rawValue,
                    status = TaskStatus.FAILED.toDbEnum,
                    resultTree = null,
                    treeIpfsHash = null,
                    totalAssetAmount = null
                )
            )
        }

        verify("failed create payout task is correctly fetched from database") {
            val result = repository.getById(taskUuid)

            assertThat(result).withMessage()
                .isEqualTo(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAssetAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = OtherTaskData(TaskStatus.FAILED)
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchAllTasksByIssuerAndOwner() {
        val requester1 = WalletAddress("aaa1")
        val issuer1 = ContractAddress("bbb1")
        val requester1Issuer1Tasks = listOf(
            createPendingTaskRecord(issuer1, requester1),
            createPendingTaskRecord(issuer1, requester1)
        )

        val issuer2 = ContractAddress("bbb2")
        val requester1Issuer2Tasks = listOf(
            createPendingTaskRecord(issuer2, requester1),
            createPendingTaskRecord(issuer2, requester1),
            createPendingTaskRecord(issuer2, requester1),
            createPendingTaskRecord(issuer2, requester1)
        )

        val requester1NullIssuerTasks = listOf(
            createPendingTaskRecord(null, requester1),
            createPendingTaskRecord(null, requester1)
        )

        val requester2 = WalletAddress("aaa2")
        val requester2Issuer1Tasks = listOf(
            createPendingTaskRecord(issuer1, requester2),
            createPendingTaskRecord(issuer1, requester2),
            createPendingTaskRecord(issuer1, requester2)
        )

        val requester2Issuer2Tasks = listOf(
            createPendingTaskRecord(issuer2, requester2)
        )

        val requester2NullIssuerTasks = listOf(
            createPendingTaskRecord(null, requester2),
            createPendingTaskRecord(null, requester2),
            createPendingTaskRecord(null, requester2)
        )

        val requester1Tasks = requester1Issuer1Tasks + requester1Issuer2Tasks + requester1NullIssuerTasks
        val requester2Tasks = requester2Issuer1Tasks + requester2Issuer2Tasks + requester2NullIssuerTasks
        val allTasks = requester1Tasks + requester2Tasks

        suppose("all tasks are stored into database") {
            dslContext.batchInsert(allTasks).execute()
        }

        verify("tasks are correctly fetched by issuer and owner") {
            assertThat(repository.getAllByIssuerAndOwner(issuer1, requester1)).withMessage()
                .containsExactlyInAnyOrderElementsOf(requester1Issuer1Tasks.toModels())
            assertThat(repository.getAllByIssuerAndOwner(issuer2, requester1)).withMessage()
                .containsExactlyInAnyOrderElementsOf(requester1Issuer2Tasks.toModels())
            assertThat(repository.getAllByIssuerAndOwner(issuer1, requester2)).withMessage()
                .containsExactlyInAnyOrderElementsOf(requester2Issuer1Tasks.toModels())
            assertThat(repository.getAllByIssuerAndOwner(issuer2, requester2)).withMessage()
                .containsExactlyInAnyOrderElementsOf(requester2Issuer2Tasks.toModels())
        }

        val issuer1Tasks = requester1Issuer1Tasks + requester2Issuer1Tasks
        val issuer2Tasks = requester1Issuer2Tasks + requester2Issuer2Tasks

        verify("tasks are correctly fetched by issuer") {
            assertThat(repository.getAllByIssuerAndOwner(issuer1, null)).withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer1Tasks.toModels())
            assertThat(repository.getAllByIssuerAndOwner(issuer2, null)).withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer2Tasks.toModels())
        }

        verify("tasks are correctly fetched by owner") {
            assertThat(repository.getAllByIssuerAndOwner(null, requester1)).withMessage()
                .containsExactlyInAnyOrderElementsOf(requester1Tasks.toModels())
            assertThat(repository.getAllByIssuerAndOwner(null, requester2)).withMessage()
                .containsExactlyInAnyOrderElementsOf(requester2Tasks.toModels())
        }

        verify("tasks are correctly fetched with null filters") {
            assertThat(repository.getAllByIssuerAndOwner(null, null)).withMessage()
                .containsExactlyInAnyOrderElementsOf(allTasks.toModels())
        }
    }

    @Test
    fun mustReturnNullWhenFetchingNonExistentCreatePayoutTaskById() {
        verify("null is returned when fetching non-existent create payout task") {
            val result = repository.getById(UUID.randomUUID())
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustCorrectlyCreatePayoutTask() {
        val taskUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(taskUuid)
        }

        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))

        val storedTaskId = suppose("create payout task is stored into database") {
            repository.createPayoutTask(
                CreatePayoutTaskParams(
                    chainId = chainId,
                    assetAddress = assetAddress,
                    requesterAddress = requesterAddress,
                    issuerAddress = issuerAddress,
                    payoutBlock = payoutBlock,
                    ignoredAssetAddresses = ignoredAssetAddresses
                )
            )
        }

        verify("correct task ID is returned") {
            assertThat(storedTaskId).withMessage()
                .isEqualTo(taskUuid)
        }

        verify("create payout task is correctly stored into database") {
            val record = dslContext.selectFrom(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
                .where(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.ID.eq(taskUuid))
                .fetchOne()
            assertThat(record).withMessage()
                .isEqualTo(
                    CreatePayoutTaskRecord(
                        id = taskUuid,
                        chainId = chainId.value,
                        assetAddress = assetAddress.rawValue,
                        blockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                        requesterAddress = requesterAddress.rawValue,
                        issuerAddress = issuerAddress.rawValue,
                        status = TaskStatus.PENDING.toDbEnum,
                        resultTree = null,
                        treeIpfsHash = null,
                        totalAssetAmount = null
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchSinglePendingTaskFromDatabase() {
        val taskUuid = UUID.randomUUID()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))

        suppose("pending task is stored into database") {
            dslContext.executeInsert(
                CreatePayoutTaskRecord(
                    id = taskUuid,
                    chainId = chainId.value,
                    assetAddress = assetAddress.rawValue,
                    blockNumber = payoutBlock.value,
                    ignoredAssetAddresses = ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                    requesterAddress = requesterAddress.rawValue,
                    issuerAddress = issuerAddress.rawValue,
                    status = TaskStatus.PENDING.toDbEnum,
                    resultTree = null,
                    treeIpfsHash = null,
                    totalAssetAmount = null
                )
            )
        }

        verify("single pending create payout task is fetched from database") {
            val result = repository.getPending()

            assertThat(result).withMessage()
                .isEqualTo(
                    PendingCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAssetAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress
                    )
                )
        }
    }

    @Test
    fun mustReturnNullWhenThereAreNoPendingTasks() {
        verify("null is returned when fetching single pending create payout task") {
            val result = repository.getPending()
            assertThat(result).withMessage()
                .isNull()
        }
    }

    @Test
    fun mustCorrectlyCompleteTask() {
        val taskUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(taskUuid)
        }

        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))

        val storedTaskId = suppose("create payout task is stored into database") {
            repository.createPayoutTask(
                CreatePayoutTaskParams(
                    chainId = chainId,
                    assetAddress = assetAddress,
                    requesterAddress = requesterAddress,
                    issuerAddress = issuerAddress,
                    payoutBlock = payoutBlock,
                    ignoredAssetAddresses = ignoredAssetAddresses
                )
            )
        }

        verify("correct task ID is returned") {
            assertThat(storedTaskId).withMessage()
                .isEqualTo(taskUuid)
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
        val totalAssetAmount = BigInteger("123")
        val expectedResult = CreatePayoutTask(
            taskId = taskUuid,
            chainId = chainId,
            assetAddress = assetAddress,
            blockNumber = payoutBlock,
            ignoredAssetAddresses = ignoredAssetAddresses,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            data = SuccessfulTaskData(
                merkleTreeRootId = treeUuid,
                merkleTreeIpfsHash = treeIpfsHash,
                totalAssetAmount = totalAssetAmount
            )
        )

        verify("task is completed") {
            val result = repository.completeTask(taskUuid, treeUuid, treeIpfsHash, totalAssetAmount)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }

        verify("successful create payout task is correctly fetched from database") {
            val result = repository.getById(taskUuid)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }
    }

    @Test
    fun mustCorrectlyFailTask() {
        val taskUuid = UUID.randomUUID()

        suppose("UUID provider will return specified UUIDs") {
            given(uuidProvider.getUuid()).willReturn(taskUuid)
        }

        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val requesterAddress = WalletAddress("b")
        val issuerAddress = ContractAddress("c")
        val ignoredAssetAddresses = setOf(WalletAddress("e"))

        val storedTaskId = suppose("create payout task is stored into database") {
            repository.createPayoutTask(
                CreatePayoutTaskParams(
                    chainId = chainId,
                    assetAddress = assetAddress,
                    requesterAddress = requesterAddress,
                    issuerAddress = issuerAddress,
                    payoutBlock = payoutBlock,
                    ignoredAssetAddresses = ignoredAssetAddresses
                )
            )
        }

        verify("correct task ID is returned") {
            assertThat(storedTaskId).withMessage()
                .isEqualTo(taskUuid)
        }

        val expectedResult = CreatePayoutTask(
            taskId = taskUuid,
            chainId = chainId,
            assetAddress = assetAddress,
            blockNumber = payoutBlock,
            ignoredAssetAddresses = ignoredAssetAddresses,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            data = OtherTaskData(TaskStatus.FAILED)
        )

        verify("task failed") {
            val result = repository.failTask(taskUuid)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }

        verify("failed create payout task is correctly fetched from database") {
            val result = repository.getById(taskUuid)

            assertThat(result).withMessage()
                .isEqualTo(expectedResult)
        }
    }

    private fun createPendingTaskRecord(issuer: ContractAddress?, requester: WalletAddress): CreatePayoutTaskRecord =
        CreatePayoutTaskRecord(
            id = UUID.randomUUID(),
            chainId = 1L,
            assetAddress = ContractAddress("a").rawValue,
            blockNumber = BlockNumber(BigInteger.TEN).value,
            ignoredAssetAddresses = emptyArray(),
            requesterAddress = requester.rawValue,
            issuerAddress = issuer?.rawValue,
            status = TaskStatus.PENDING.toDbEnum,
            resultTree = null,
            treeIpfsHash = null,
            totalAssetAmount = null
        )

    private fun List<CreatePayoutTaskRecord>.toModels(): List<CreatePayoutTask> =
        map {
            CreatePayoutTask(
                taskId = it.id!!,
                chainId = ChainId(it.chainId!!),
                assetAddress = ContractAddress(it.assetAddress!!),
                blockNumber = BlockNumber(it.blockNumber!!),
                ignoredAssetAddresses = emptySet(),
                requesterAddress = WalletAddress(it.requesterAddress!!),
                issuerAddress = it.issuerAddress?.let { ContractAddress(it) },
                data = OtherTaskData(TaskStatus.PENDING)
            )
        }
}
