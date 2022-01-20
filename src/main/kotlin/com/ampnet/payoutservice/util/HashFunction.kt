package com.ampnet.payoutservice.util

enum class HashFunction(private val hashFn: (String) -> Hash) : (String) -> Hash {
    IDENTITY({ Hash(it) }),
    FIXED({ Hash("0") }),
    SIMPLE({ Hash("--" + it.hashCode().toString() + "//") }),
    KECCAK_256({ TODO("implement keccak256 hashing") });

    override operator fun invoke(arg: String) = hashFn(arg)
}
