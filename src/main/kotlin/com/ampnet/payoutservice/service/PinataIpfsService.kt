package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.model.json.PinataResponse
import com.ampnet.payoutservice.util.IpfsHash
import mu.KLogging
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
class PinataIpfsService(
    private val applicationProperties: ApplicationProperties,
    private val restTemplate: RestTemplate
) : IpfsService {

    companion object : KLogging()

    override fun pinJsonToIpfs(json: Any): IpfsHash {
        val uri = UriComponentsBuilder.fromUriString(applicationProperties.ipfs.url + "/pinning/pinJSONToIPFS")
            .build()
            .toUri()
        val request = RequestEntity.post(uri)
            .header("pinata_api_key", applicationProperties.ipfs.apiKey)
            .header("pinata_secret_api_key", applicationProperties.ipfs.secretApiKey)
            .body(json)

        try {
            val response = restTemplate.exchange(request, PinataResponse::class.java)

            if (response.statusCode.is2xxSuccessful) {
                return response.body?.ipfsHash?.let { IpfsHash(it) } ?: run {
                    logger.warn { "IPFS hash is missing on upload response" }
                    throw InternalException(ErrorCode.IPFS_UPLOAD_FAILED, "IPFS upload failed")
                }
            }

            logger.warn { "IPFS upload failed" }
            throw InternalException(ErrorCode.IPFS_UPLOAD_FAILED, "IPFS upload failed")
        } catch (ex: RestClientException) {
            logger.warn(ex) { "IPFS client call exception" }
            throw InternalException(ErrorCode.IPFS_UPLOAD_FAILED, "IPFS upload failed")
        }
    }
}
