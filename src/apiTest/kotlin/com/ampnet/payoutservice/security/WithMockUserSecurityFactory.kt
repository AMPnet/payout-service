package com.ampnet.payoutservice.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContextFactory

class WithMockUserSecurityFactory : WithSecurityContextFactory<WithMockUser> {

    override fun createSecurityContext(annotation: WithMockUser): SecurityContext {
        val token = UsernamePasswordAuthenticationToken(annotation.address, "password", null)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = token
        return context
    }
}
