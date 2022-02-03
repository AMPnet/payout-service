package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.controller.request.FetchMerkleTreePathRequest
import com.ampnet.payoutservice.controller.request.FetchMerkleTreeRequest
import com.ampnet.payoutservice.controller.response.FetchMerkleTreePathResponse
import com.ampnet.payoutservice.controller.response.FetchMerkleTreeResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class PayoutInfoController(private val merkleTreeRepository: MerkleTreeRepository) {

    companion object : KLogging()

    @GetMapping("/payout_info/{chainId}/{assetAddress}/tree/{rootHash}")
    fun getPayoutTree(
        @PathVariable chainId: Long,
        @PathVariable assetAddress: String,
        @PathVariable rootHash: String
    ): ResponseEntity<FetchMerkleTreeResponse> {
        val request = FetchMerkleTreeRequest(
            rootHash = Hash(rootHash),
            chainId = ChainId(chainId),
            contractAddress = ContractAddress(assetAddress)
        )
        logger.debug { "Fetching Merkle tree: $request" }

        val tree = merkleTreeRepository.fetchTree(request)
            ?: throw ResourceNotFoundException(
                ErrorCode.PAYOUT_MERKLE_TREE_NOT_FOUND,
                "Payout does not exist for specified parameters"
            )

        return ResponseEntity.ok(FetchMerkleTreeResponse(tree))
    }

    @Suppress("ThrowsCount")
    @GetMapping("/payout_info/{chainId}/{assetAddress}/tree/{rootHash}/path/{walletAddress}")
    fun getPayoutPath(
        @PathVariable chainId: Long,
        @PathVariable assetAddress: String,
        @PathVariable rootHash: String,
        @PathVariable walletAddress: String
    ): ResponseEntity<FetchMerkleTreePathResponse> {
        val request = FetchMerkleTreePathRequest(
            rootHash = Hash(rootHash),
            chainId = ChainId(chainId),
            contractAddress = ContractAddress(assetAddress),
            walletAddress = WalletAddress(walletAddress)
        )
        logger.debug { "Fetching payout path for: $request" }

        val payoutExists = merkleTreeRepository.containsAddress(request)

        if (payoutExists.not()) {
            throw ResourceNotFoundException(
                ErrorCode.PAYOUT_NOT_FOUND_FOR_ACCOUNT,
                "Payout does not exist for specified parameters or account is not included in payout"
            )
        }

        val tree = merkleTreeRepository.fetchTree(request.toFetchMerkleTreeRequest)
            ?: throw ResourceNotFoundException(
                ErrorCode.PAYOUT_NOT_FOUND_FOR_ACCOUNT,
                "Payout does not exist for specified parameters"
            )

        val (path, accountBalance) = tree.leafNodesByAddress[request.walletAddress]?.let {
            tree.pathTo(it.value.data)?.let { path -> Pair(path, it.value.data) }
        }
            ?: throw ResourceNotFoundException(
                ErrorCode.PAYOUT_NOT_FOUND_FOR_ACCOUNT,
                "Account is not included in payout"
            )

        return ResponseEntity.ok(
            FetchMerkleTreePathResponse(
                accountBalance.address.rawValue,
                accountBalance.balance.rawValue,
                path
            )
        )
    }
}
