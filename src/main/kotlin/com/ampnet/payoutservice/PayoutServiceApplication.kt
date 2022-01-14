package com.ampnet.payoutservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PayoutServiceApplication

fun main(vararg args: String) {
    runApplication<PayoutServiceApplication>(*args)
}
