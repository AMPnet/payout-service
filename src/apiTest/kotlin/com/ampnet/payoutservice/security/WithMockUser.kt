package com.ampnet.payoutservice.security

import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import org.springframework.security.test.context.support.WithSecurityContext

@Retention(value = AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@WithSecurityContext(factory = WithMockUserSecurityFactory::class)
annotation class WithMockUser(
    val address: String = HardhatTestContainer.accountAddress1
)
