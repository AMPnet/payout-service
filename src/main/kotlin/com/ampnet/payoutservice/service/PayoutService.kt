package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.stereotype.Service

@Service
class PayoutService(
    private val merkleTreeRepository: MerkleTreeRepository,
    private val blockchainService: BlockchainService
) : IPayoutService {

    companion object : KLogging()

    override fun createPayout(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        payoutBlock: BlockNumber
    ): Hash {
        logger.info {
            "Payout request for chain ID: $chainId, asset address: $assetAddress," +
                " requester address: $requesterAddress, payout block: $payoutBlock"
        }
        val assetOwner = blockchainService.getAssetOwner(chainId, assetAddress)

        if (assetOwner != requesterAddress) {
            logger.warn { "Requester is not asset owner" }
            throw InvalidRequestException(
                ErrorCode.USER_NOT_ASSET_OWNER,
                "User with wallet address: $requesterAddress is not the owner of asset contract: $assetAddress"
            )
        }

        val balances = blockchainService.fetchErc20AccountBalances(
            chainId = chainId,
            erc20ContractAddress = assetAddress,
            startBlock = null, // TODO provide some default values per chain ID
            endBlock = payoutBlock
        )

        val tree = MerkleTree(balances, HashFunction.KECCAK_256)

        return if (merkleTreeRepository.treeExists(tree.root.hash, chainId, assetAddress)) {
            logger.debug { "Merkle tree already exists, returning root hash" }
            tree.root.hash
        } else {
            logger.debug { "Storing Merkle tree into the database" }
            merkleTreeRepository.storeTree(tree, chainId, assetAddress, payoutBlock)
        }
    }
}
