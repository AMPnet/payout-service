package com.ampnet.payoutservice.service

import org.springframework.stereotype.Service
import java.util.UUID

interface UuidProvider {
    fun getUuid(): UUID
}

@Service
class RandomUuidProvider : UuidProvider {
    override fun getUuid(): UUID = UUID.randomUUID()
}
