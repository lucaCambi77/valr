package com.valr.task.integration

import com.valr.task.domain.*
import com.valr.task.service.ExchangeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
class ExchangeControllerIntegrationTest {

    @MockBean
    private lateinit var exchangeService: ExchangeService

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val testUsername = "testuser@example.com"
    private val testPassword = "password123"
    private lateinit var testUserId: String

    @BeforeEach
    fun setup() {
        val userRequestJson = """
            {
                "name": "Test User",
                "email": "$testUsername",
                "password": "$testPassword"
            }
        """.trimIndent()

        val userIdResponse = mockMvc.perform(
            post("/account/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userRequestJson)
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString

        testUserId = userIdResponse
    }

    @Test
    fun `test place limit order`() {
        val id = UUID.randomUUID().toString()

        val orderRequestJson = """
            {
                "id": "$id",
                "price": "20000.00",
                "quantity": "0.5",
                "side": "BUY",
                "pair": "BTCUSDC"
            }
        """.trimIndent()

        `when`(
            exchangeService.placeOrder(
                Order(
                    id = id, price = BigDecimal("20000.00"), quantity = BigDecimal("0.5"), side = OrderSide.BUY, user = testUserId, status = OrderStatus.OPEN, filledQuantity = BigDecimal.ZERO, pair = "BTCUSDC"
                )
            )
        ).thenReturn(
            id
        )

        mockMvc.perform(
            post("/orders/limit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson)
                .with(httpBasic(testUserId, testPassword))
        )
            .andExpect(status().isAccepted)
            .andExpect(content().string(id))
    }

    @Test
    fun `test cancel order`() {
        val cancelRequestJson = """
            {
                "orderId": "${UUID.randomUUID()}",
                "pair": "BTCUSDC"
            }
        """.trimIndent()

        mockMvc.perform(
            delete("/orders/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelRequestJson)
                .with(httpBasic(testUserId, testPassword))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `test recent trades`() {
        val tradesJson = """
            [
                {
                    "id": "trade1",
                    "price": "20000.00",
                    "quantity": "0.5",
                    "currencyPair": "BTCUSDC",
                    "takerSide": "BUY",
                    "tradedAt": "2024-08-16T12:00:00Z",
                    "quoteVolume": "10000.00",
                    "sequenceId": 1
                },
                {
                    "id": "trade2",
                    "price": "19900.00",
                    "quantity": "1.0",
                    "currencyPair": "BTCUSDC",
                    "takerSide": "SELL",
                    "tradedAt": "2024-08-16T12:01:00Z",
                    "quoteVolume": "10000.00",
                    "sequenceId": 1
                }
            ]
        """.trimIndent()

        `when`(exchangeService.getRecentTrades("BTCUSDC", 10)).thenReturn(
            listOf(
                Trade(id = "trade1", price = "20000.00", quantity = "0.5", currencyPair = "BTCUSDC", takerSide = OrderSide.BUY, tradedAt = "2024-08-16T12:00:00Z", quoteVolume = "10000.00", sequenceId = 1),
                Trade(id = "trade2", price = "19900.00", quantity = "1.0", currencyPair = "BTCUSDC", takerSide = OrderSide.SELL, tradedAt = "2024-08-16T12:01:00Z", quoteVolume = "10000.00", sequenceId = 1)
            )
        )

        mockMvc.perform(
            get("/BTCUSDC/tradehistory")
                .param("limit", "10")
                .with(httpBasic(testUserId, testPassword))
        )
            .andExpect(status().isOk)
            .andExpect(content().json(tradesJson))
    }

    @Test
    fun `test order book`() {
        val orderBookJson = """
                {
                    "asks": [
                        {"side": "BUY", "quantity": "0.5", "price": "20000.00", "currencyPair": "BTCUSDC", "orderCount": 1},
                        {"side": "SELL", "quantity": "1.0", "price": "19900.00", "currencyPair": "BTCUSDC", "orderCount": 2},
                        {"side": "SELL", "quantity": "0.2", "price": "21000.00", "currencyPair": "BTCUSDC", "orderCount": 3},
                        {"side": "BUY", "quantity": "0.3", "price": "22000.00", "currencyPair": "BTCUSDC", "orderCount": 4}
                    ],
                    "lastChange": "2024-08-16T12:00:00Z",
                    "sequenceNumber": 1
                }
                """.trimIndent()

        `when`(exchangeService.orderBook("BTCUSDC")).thenReturn(
            OrderBookResponse(
                asks = listOf(
                    OrderSummary(
                        side = OrderSide.BUY,
                        quantity = "0.5",
                        price = "20000.00",
                        currencyPair = "BTCUSDC",
                        orderCount = 1
                    ),
                    OrderSummary(
                        side = OrderSide.SELL,
                        quantity = "1.0",
                        price = "19900.00",
                        currencyPair = "BTCUSDC",
                        orderCount = 2
                    ),
                    OrderSummary(
                        side = OrderSide.SELL,
                        quantity = "0.2",
                        price = "21000.00",
                        currencyPair = "BTCUSDC",
                        orderCount = 3
                    ),
                    OrderSummary(
                        side = OrderSide.BUY,
                        quantity = "0.3",
                        price = "22000.00",
                        currencyPair = "BTCUSDC",
                        orderCount = 4
                    )
                ),
                lastChange = "2024-08-16T12:00:00Z",
                sequenceNumber = 1
            )
        )
        mockMvc.perform(
            get("/BTCUSDC/orderbook")
                .with(httpBasic(testUserId, testPassword))
        )
            .andExpect(status().isOk)
            .andExpect(content().json(orderBookJson))
    }
}
