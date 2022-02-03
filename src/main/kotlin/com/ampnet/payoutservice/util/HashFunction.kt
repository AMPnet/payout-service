package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.generated.jooq.enums.HashFunction as DbHashFunction
import org.web3j.crypto.Hash.sha3 as keccak256

enum class HashFunction(val toDbEnum: DbHashFunction, private val hashFn: (String) -> Hash) : (String) -> Hash {
    IDENTITY(DbHashFunction.IDENTITY, { Hash(it) }),
    FIXED(DbHashFunction.FIXED, { Hash("0") }),
    KECCAK_256(DbHashFunction.KECCAK_256, { Hash(keccak256(it)) });

    override operator fun invoke(arg: String) = hashFn(arg)

    companion object {
        fun fromDbEnum(value: DbHashFunction): HashFunction {
            return values().find { it.toDbEnum == value }
                ?: throw IllegalStateException("DB enum not added to code: $value")
        }
    }
}
