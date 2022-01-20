package com.ampnet.payoutservice.util

import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Uint
import java.math.BigInteger

@JvmInline
value class Hash(val value: String) {
    operator fun plus(other: Hash): Hash = Hash(value + other.value)
}

@JvmInline
value class WalletAddress(val value: Address) : Comparable<WalletAddress> {
    constructor(value: String) : this(Address(value))

    val rawValue: String
        get() = value.value

    fun abiEncode(): String = TypeEncoder.encode(value)

    override fun compareTo(other: WalletAddress): Int = value.toUint().value.compareTo(other.value.toUint().value)
}

@JvmInline
value class Balance(val value: Uint) {
    constructor(value: BigInteger) : this(Uint(value))

    fun abiEncode(): String = TypeEncoder.encode(value)
}

@JvmInline
value class ChainId(val value: Long)

@JvmInline
value class BlockNumber(val value: BigInteger)

@JvmInline
value class ContractAddress(val value: Address) {
    constructor(value: String) : this(Address(value))

    val rawValue: String
        get() = value.value
}
