package service

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.service.IpfsService
import com.ampnet.payoutservice.service.PayoutServiceImpl
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.math.BigInteger
import org.mockito.kotlin.verify as verifyMock

class PayoutServiceTest : TestBase() {

    @Test
    fun mustCorrectlyCreatePayoutWhenMerkleTreeDoesNotAlreadyExist() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(requesterAddress)
        }

        val payoutBlock = BlockNumber(BigInteger.TEN)
        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )

        suppose("some asset balances are fetched") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willReturn(accountBalances)
        }

        val tree = MerkleTree(accountBalances, HashFunction.KECCAK_256)
        val ipfsService = mock<IpfsService>()
        val ipfsHash = IpfsHash("testIpfsHash")

        suppose("Merkle tree is stored to IPFS") {
            given(ipfsService.pinJsonToIpfs(tree))
                .willReturn(ipfsHash)
        }

        val repository = mock<MerkleTreeRepository>()

        suppose("Merkle tree does not exist in the database") {
            given(repository.treeExists(tree.root.hash, chainId, assetAddress))
                .willReturn(false)
        }

        suppose("Merkle tree is stored in the database and root hash is returned") {
            given(repository.storeTree(tree, chainId, assetAddress, payoutBlock))
                .willReturn(tree.root.hash)
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }

        val service = PayoutServiceImpl(repository, ipfsService, blockchainService, properties)

        verify("payout is correctly created and Merkle tree root hash is returned") {
            val response = service.createPayout(chainId, assetAddress, requesterAddress, payoutBlock)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutResponse(
                        payoutBlockNumber = payoutBlock.value,
                        merkleRootHash = tree.root.hash.value,
                        merkleTreeIpfsHash = ipfsHash.value
                    )
                )
        }

        verify("correct service and repository calls are made") {
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(repository)
                .treeExists(tree.root.hash, chainId, assetAddress)
            verifyMock(repository)
                .storeTree(tree, chainId, assetAddress, payoutBlock)
            verifyNoMoreInteractions(repository)
        }
    }

    @Test
    fun mustCorrectlyCreatePayoutWhenMerkleTreeAlreadyExist() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(requesterAddress)
        }

        val payoutBlock = BlockNumber(BigInteger.TEN)
        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )

        suppose("some asset balances are fetched") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willReturn(accountBalances)
        }

        val tree = MerkleTree(accountBalances, HashFunction.KECCAK_256)
        val ipfsService = mock<IpfsService>()
        val ipfsHash = IpfsHash("testIpfsHash")

        suppose("Merkle tree is stored to IPFS") {
            given(ipfsService.pinJsonToIpfs(tree))
                .willReturn(ipfsHash)
        }

        val repository = mock<MerkleTreeRepository>()

        suppose("Merkle tree already exists in the database") {
            given(repository.treeExists(tree.root.hash, chainId, assetAddress))
                .willReturn(true)
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }

        val service = PayoutServiceImpl(repository, ipfsService, blockchainService, properties)

        verify("payout is correctly created and Merkle tree root hash is returned") {
            val response = service.createPayout(chainId, assetAddress, requesterAddress, payoutBlock)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutResponse(
                        payoutBlockNumber = payoutBlock.value,
                        merkleRootHash = tree.root.hash.value,
                        merkleTreeIpfsHash = ipfsHash.value
                    )
                )
        }

        verify("correct service and repository calls are made") {
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(repository)
                .treeExists(tree.root.hash, chainId, assetAddress)
            verifyNoMoreInteractions(repository)
        }
    }

    @Test
    fun mustThrowExceptionWhenRequestingUserIsNotAssetOwner() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is not asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(WalletAddress("2"))
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }

        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ipfsService = mock<IpfsService>()
        val repository = mock<MerkleTreeRepository>()
        val service = PayoutServiceImpl(repository, ipfsService, blockchainService, properties)

        verify("InvalidRequestException exception is thrown") {
            assertThrows<InvalidRequestException>(message) {
                service.createPayout(chainId, assetAddress, requesterAddress, payoutBlock)
            }
        }

        verify("correct service and repository calls are made") {
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyNoMoreInteractions(blockchainService)

            verifyNoInteractions(ipfsService)
            verifyNoInteractions(repository)
        }
    }

    @Test
    fun mustCorrectlyCreatePayoutForNonOwnerWhenAssetOwnerIsNotChecked() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )

        suppose("some asset balances are fetched") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willReturn(accountBalances)
        }

        val tree = MerkleTree(accountBalances, HashFunction.KECCAK_256)
        val ipfsService = mock<IpfsService>()
        val ipfsHash = IpfsHash("testIpfsHash")

        suppose("Merkle tree is stored to IPFS") {
            given(ipfsService.pinJsonToIpfs(tree))
                .willReturn(ipfsHash)
        }

        val repository = mock<MerkleTreeRepository>()

        suppose("Merkle tree does not exist in the database") {
            given(repository.treeExists(tree.root.hash, chainId, assetAddress))
                .willReturn(false)
        }

        suppose("Merkle tree is stored in the database and root hash is returned") {
            given(repository.storeTree(tree, chainId, assetAddress, payoutBlock))
                .willReturn(tree.root.hash)
        }

        val properties = suppose("asset owner will not be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = false }
        }

        val service = PayoutServiceImpl(repository, ipfsService, blockchainService, properties)
        val requesterAddress = WalletAddress("1")

        verify("payout is correctly created and Merkle tree root hash is returned") {
            val response = service.createPayout(chainId, assetAddress, requesterAddress, payoutBlock)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutResponse(
                        payoutBlockNumber = payoutBlock.value,
                        merkleRootHash = tree.root.hash.value,
                        merkleTreeIpfsHash = ipfsHash.value
                    )
                )
        }

        verify("correct service and repository calls are made") {
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(repository)
                .treeExists(tree.root.hash, chainId, assetAddress)
            verifyMock(repository)
                .storeTree(tree, chainId, assetAddress, payoutBlock)
            verifyNoMoreInteractions(repository)
        }
    }
}
