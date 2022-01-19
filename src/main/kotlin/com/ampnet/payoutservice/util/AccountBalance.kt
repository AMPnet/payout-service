package com.ampnet.payoutservice.util

data class AccountBalance(val address: WalletAddress, val balance: Balance) : Comparable<AccountBalance> {
    fun abiEncode(): String = address.abiEncode() + balance.abiEncode()

    override fun compareTo(other: AccountBalance): Int = address.compareTo(other.address)
}
