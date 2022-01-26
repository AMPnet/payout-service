package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.stereotype.Service

@Service
class PayoutServiceImpl(
    private val merkleTreeRepository: MerkleTreeRepository,
    private val ipfsService: IpfsService,
    private val blockchainService: BlockchainService,
    private val applicationProperties: ApplicationProperties
) : PayoutService {

    companion object : KLogging()

    private val chainHandler = ChainPropertiesHandler(applicationProperties)

    override fun createPayout(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        payoutBlock: BlockNumber
    ): CreatePayoutResponse {
        logger.info {
            "Payout request for chain ID: $chainId, asset address: $assetAddress," +
                " requester address: $requesterAddress, payout block: $payoutBlock"
        }

        checkAssetOwnerIfNeeded(chainId, assetAddress, requesterAddress)

        val balances = blockchainService.fetchErc20AccountBalances(
            chainId = chainId,
            erc20ContractAddress = assetAddress,
            startBlock = chainHandler.getChainProperties(chainId)?.startBlockNumber?.let { BlockNumber(it) },
            endBlock = payoutBlock
        )

        val tree = MerkleTree(balances, HashFunction.KECCAK_256)
        val ipfsHash = ipfsService.pinJsonToIpfs(tree)

        val rootHash = if (merkleTreeRepository.treeExists(tree.root.hash, chainId, assetAddress)) {
            logger.debug { "Merkle tree already exists, returning root hash" }
            tree.root.hash
        } else {
            logger.debug { "Storing Merkle tree into the database" }
            merkleTreeRepository.storeTree(tree, chainId, assetAddress, payoutBlock)
        }

        return CreatePayoutResponse(
            payoutBlockNumber = payoutBlock.value,
            merkleRootHash = rootHash.value,
            merkleTreeIpfsHash = ipfsHash.value
        )
    }

    private fun checkAssetOwnerIfNeeded(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress
    ) {
        if (applicationProperties.payout.checkAssetOwner) {
            val assetOwner = blockchainService.getAssetOwner(chainId, assetAddress)

            if (assetOwner != requesterAddress) {
                logger.warn { "Requester is not asset owner" }
                throw InvalidRequestException(
                    ErrorCode.USER_NOT_ASSET_OWNER,
                    "User with wallet address: $requesterAddress is not the owner of asset contract: $assetAddress"
                )
            }
        }
    }
}
