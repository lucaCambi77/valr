package com.valr.task.integration

import org.hamcrest.Matchers.emptyOrNullString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var userId: String
    private var password: String = "password"

    @BeforeEach
    fun setup() {
        val userJson = """
            {
                "name": "New User",
                "email": "newuser@example.com",
                "password" : "$password"
            }
        """.trimIndent()

        val result: MvcResult = mockMvc.perform(
            post("/account/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson)
        )
            .andExpect(status().isCreated)
            .andExpect(content().string(not(emptyOrNullString())))
            .andReturn()

        userId = result.response.contentAsString
    }

    @Test
    fun `test createUser creates a user`() {
        val userJson = """
            {
                "name": "another User",
                "email": "anotheruser@example.com",
                "password" : "another password"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/account/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson)
        )
            .andExpect(status().isCreated)
            .andExpect(content().string(not(emptyOrNullString())))  // Check that the response is not empty or null
    }

    @Test
    fun `test updateWallet updates the user's wallet`() {
        val walletUpdateJson = """
            {
                "baseBalances": {
                    "BTC": 0.5
                },
                "quoteBalances": {
                    "USDC": 5000.0
                }
            }
        """.trimIndent()

        mockMvc.perform(
            patch("/account/wallet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(walletUpdateJson)
                .with(httpBasic(userId, password))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.wallet.baseBalances.BTC").value("0.5"))
            .andExpect(jsonPath("$.wallet.quoteBalances.USDC").value("5000.0"))
    }
}

