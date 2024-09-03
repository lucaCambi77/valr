package com.valr.task.integration

import com.valr.task.domain.*
import com.valr.task.service.ExchangeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.Instant
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

        // Create an ArgumentCaptor to capture the Order object passed to placeOrder
        val orderCaptor = argumentCaptor<Order>()

        whenever(exchangeService.placeOrder(orderCaptor.capture())).thenReturn(id)

        val orderRequestJson = """
            {
                "price": "20000.00",
                "quantity": "0.5",
                "side": "BUY",
                "pair": "BTCUSDC"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/orders/limit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson)
                .with(httpBasic(testUserId, testPassword))
        )
            .andExpect(status().isAccepted)
            .andExpect(content().string(id))

        val capturedOrder = orderCaptor.firstValue
        assert(capturedOrder.user == testUserId) { "User ID was not correctly set" }
        assert(capturedOrder.price == BigDecimal("20000.00")) { "Price was not correctly set" }
        assert(capturedOrder.quantity == BigDecimal("0.5")) { "Quantity was not correctly set" }
        assert(capturedOrder.pair == "BTCUSDC") { "Currency pair was not correctly set" }
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

        `when`(exchangeService.tradeHistory("BTCUSDC", 10)).thenReturn(
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
                        {"side": "SELL", "quantity": "1.0", "price": "19900.00", "currencyPair": "BTCUSDC", "orderCount": 2},
                        {"side": "SELL", "quantity": "0.2", "price": "21000.00", "currencyPair": "BTCUSDC", "orderCount": 3}
                    ],
                    "bids": [
                        {"side": "BUY", "quantity": "0.5", "price": "20000.00", "currencyPair": "BTCUSDC", "orderCount": 1},
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
                    )
                ),
                bids = listOf(
                    OrderSummary(
                        side = OrderSide.BUY,
                        quantity = "0.5",
                        price = "20000.00",
                        currencyPair = "BTCUSDC",
                        orderCount = 1
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

    @Test
    fun `test orderStatus endpoint returns correct status`() {
        val currencyPair = "BTCUSDC"
        val orderId = "test-order-id"

        val testOrderStatusResponse = OrderStatusResponse(
            orderId = orderId,
            orderStatusType = OrderStatus.OPEN,
            currencyPair = currencyPair,
            originalPrice = "20000.00",
            remainingQuantity = "1.0",
            originalQuantity = "1.0",
            orderSide = OrderSide.BUY,
            orderType = "limit",
            failedReason = "failed",
            orderUpdatedAt = Instant.now().toString(),
            orderCreatedAt = Instant.now().toString()
        )

        `when`(exchangeService.orderStatus(currencyPair, orderId))
            .thenReturn(listOf(testOrderStatusResponse))

        mockMvc.perform(
            get("/orders/$currencyPair/status/$orderId")
                .with(httpBasic(testUserId, testPassword))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].orderId").value(orderId))
            .andExpect(jsonPath("$[0].orderStatusType").value(OrderStatus.OPEN.toString()))
            .andExpect(jsonPath("$[0].currencyPair").value(currencyPair))
            .andExpect(jsonPath("$[0].originalPrice").value("20000.00"))
            .andExpect(jsonPath("$[0].remainingQuantity").value("1.0"))
            .andExpect(jsonPath("$[0].orderSide").value(OrderSide.BUY.toString()))
    }

}
