package com.ampnet.payoutservice

import org.assertj.core.api.Assert
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
abstract class TestBase {

    companion object {
        data class SupposeMessage(val message: String)

        data class VerifyMessage(val message: String) {
            fun <A : Assert<A, B>, B> Assert<A, B>.withMessage(): A = this.`as`(message)
            fun <A : Assert<A, B>, B> Assert<A, B>.withIndexedMessage(index: Int): A = this.`as`("[$index] $message")
        }
    }

    protected fun <R> suppose(description: String, function: SupposeMessage.() -> R): R {
        return function.invoke(SupposeMessage(description))
    }

    protected fun verify(description: String, function: VerifyMessage.() -> Unit) {
        function.invoke(VerifyMessage(description))
    }
}
