package com.ampnet.payoutservice

import org.assertj.core.api.Assert
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
abstract class TestBase {

    companion object {
        data class Message(val message: String) {
            fun <A : Assert<A, B>, B> Assert<A, B>.withMessage(): A = this.`as`(message)
        }
    }

    protected fun <R> suppose(description: String, function: Message.() -> R): R {
        return function.invoke(Message(description))
    }

    protected fun verify(description: String, function: Message.() -> Unit) {
        function.invoke(Message(description))
    }
}
