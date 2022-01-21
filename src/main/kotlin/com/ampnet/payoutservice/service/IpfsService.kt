package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.util.IpfsHash

interface IpfsService {
    fun pinJsonToIpfs(json: Any): IpfsHash
}
