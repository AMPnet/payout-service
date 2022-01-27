package com.ampnet.payoutservice.util

import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Uint
import java.math.BigInteger

@JvmInline
value class Hash private constructor(val value: String) {
    companion object {
        operator fun invoke(value: String) = Hash(value.lowercase())
    }

    operator fun plus(other: Hash): Hash = Hash(value + other.value.replaceFirst("0x", ""))
}

@JvmInline
value class WalletAddress private constructor(val value: Address) : Comparable<WalletAddress> {
    companion object {
        operator fun invoke(value: Address) = WalletAddress(value.toString())
    }

    constructor(value: String) : this(Address(value.lowercase()))

    val rawValue: String
        get() = value.value

    fun abiEncode(): String = TypeEncoder.encode(value)

    override fun compareTo(other: WalletAddress): Int = value.toUint().value.compareTo(other.value.toUint().value)
}

@JvmInline
value class Balance(val value: Uint) {
    constructor(value: BigInteger) : this(Uint(value))

    val rawValue: BigInteger
        get() = value.value

    fun abiEncode(): String = TypeEncoder.encode(value)
}

@JvmInline
value class ChainId(val value: Long)

@JvmInline
value class BlockNumber(val value: BigInteger)

@JvmInline
value class ContractAddress private constructor(val value: Address) {
    companion object {
        operator fun invoke(value: Address) = ContractAddress(value.toString())
    }

    constructor(value: String) : this(Address(value.lowercase()))

    val rawValue: String
        get() = value.value
}

@JvmInline
value class IpfsHash(val value: String)
