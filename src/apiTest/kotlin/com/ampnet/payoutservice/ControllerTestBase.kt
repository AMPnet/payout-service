package com.ampnet.payoutservice

import com.ampnet.payoutservice.TestBase.Companion.VerifyMessage
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.ErrorResponse
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.testcontainers.PostgresTestContainer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@ExtendWith(value = [SpringExtension::class, RestDocumentationExtension::class])
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTestBase : TestBase() {

    @Suppress("unused")
    protected val postgresContainer = PostgresTestContainer()

    @Suppress("unused")
    protected val hardhatContainer = HardhatTestContainer()

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach(wac: WebApplicationContext, restDocumentation: RestDocumentationContextProvider) {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .apply<DefaultMockMvcBuilder>(MockMvcRestDocumentation.documentationConfiguration(restDocumentation))
            .alwaysDo<DefaultMockMvcBuilder>(
                MockMvcRestDocumentation.document(
                    "{ClassName}/{methodName}",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint())
                )
            )
            .build()
    }

    protected fun getResponseErrorCode(errorCode: ErrorCode): String {
        return errorCode.categoryCode + errorCode.specificCode
    }

    protected fun VerifyMessage.verifyResponseErrorCode(result: MvcResult, errorCode: ErrorCode) {
        val response: ErrorResponse = objectMapper.readValue(result.response.contentAsString)
        val expectedErrorCode = getResponseErrorCode(errorCode)

        Assertions.assertThat(response.errCode).withMessage()
            .isEqualTo(expectedErrorCode)
    }
}
