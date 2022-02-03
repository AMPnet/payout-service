package com.ampnet.payoutservice.util

data class AccountBalance(val address: WalletAddress, val balance: Balance) {
    fun abiEncode(): String = address.abiEncode() + balance.abiEncode()
}
