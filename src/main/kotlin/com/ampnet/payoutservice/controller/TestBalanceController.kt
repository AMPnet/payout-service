package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigInteger

@RestController
class TestBalanceController(private val blockchainService: BlockchainService) {

    @GetMapping("/test-fetch")
    fun testFetchingBalances(): ResponseEntity<Void> {
        val balances1 = blockchainService.fetchErc20AccountBalances(
            chainId = Chain.MATIC_TESTNET_MUMBAI.id,
            erc20ContractAddress = ContractAddress("0x4f70942a22f6e3221598640302117c54bac54744"), // test USDC coin
            startBlock = null,
            endBlock = BlockNumber(BigInteger("23850349")) // latest transaction at the moment of writing this
        )
        val balances2 = blockchainService.fetchErc20AccountBalances(
            chainId = Chain.MATIC_TESTNET_MUMBAI.id,
            erc20ContractAddress = ContractAddress("0x4f70942a22f6e3221598640302117c54bac54744"),
            startBlock = null,
            endBlock = BlockNumber(BigInteger("23850299")) // account 0x2833A678ef4a323D982FBB3EA4F567e625A5f118 recieves 250 USDC
        )
        val balances3 = blockchainService.fetchErc20AccountBalances(
            chainId = Chain.MATIC_TESTNET_MUMBAI.id,
            erc20ContractAddress = ContractAddress("0x4f70942a22f6e3221598640302117c54bac54744"),
            startBlock = null,
            endBlock = BlockNumber(BigInteger("23850298")) // one block before
        )

        println("-----------------------------------------------------------------------------------------------------")
        println(balances1)
        println("-----------------------------------------------------------------------------------------------------")
        println(balances2)
        println("-----------------------------------------------------------------------------------------------------")
        println(balances3)
        println("-----------------------------------------------------------------------------------------------------")

        return ResponseEntity.ok(null)
    }
}
