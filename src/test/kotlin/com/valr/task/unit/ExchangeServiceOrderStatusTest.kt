package com.valr.task.unit

import com.valr.task.domain.*
import com.valr.task.service.CurrencyPairService
import com.valr.task.service.ExchangeService
import com.valr.task.service.UserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class ExchangeServiceOrderStatusTest {

    private lateinit var exchangeService: ExchangeService
    private lateinit var userService: UserService
    private lateinit var currencyPairService: CurrencyPairService

    @BeforeEach
    fun setup() {
        userService = mock(UserService::class.java)
        currencyPairService = mock(CurrencyPairService::class.java)
        exchangeService = ExchangeService(userService, currencyPairService, BigDecimal("0.01"), BigDecimal("0.001"))

        val currencyPairSymbol = "BTCUSDC"
        val baseCurrency = "BTC"
        val quoteCurrency = "USDC"

        // Mock Currency Pair
        val currencyPair = CurrencyPair(
            symbol = currencyPairSymbol,
            baseCurrency = baseCurrency,
            quoteCurrency = quoteCurrency,
            shortName = "BTC/USDC",
            baseDecimalPlaces = 2
        )

        `when`(currencyPairService.getAllPairs()).thenReturn(mutableMapOf(currencyPairSymbol to currencyPair))
    }

    @Test
    fun `test place buy order with insufficient quote currency results in failed order`() {
        val userId = "user1"
        val orderId = UUID.randomUUID().toString()
        val currencyPairSymbol = "BTCUSDC"
        val baseCurrency = "BTC"
        val quoteCurrency = "USDC"
        val price = BigDecimal("20000.00")
        val quantity = BigDecimal("1.0")

        // Mock User with insufficient USDC balance
        val user = User(
            id = userId,
            email = "test@example.com",
            wallet = Wallet(
                baseBalances = mutableMapOf(baseCurrency to BigDecimal("0.5")),
                quoteBalances = mutableMapOf(quoteCurrency to BigDecimal("10000.00"))
            )
        )

        `when`(userService.get(userId)).thenReturn(user)

        // Create a Buy Order
        val order = Order(
            id = orderId,
            price = price,
            quantity = quantity,
            side = OrderSide.BUY,
            pair = currencyPairSymbol,
            user = userId,
            status = OrderStatus.OPEN,
            filledQuantity = BigDecimal.ZERO,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Place the Buy Order
        exchangeService.placeOrder(order)

        // Verify the order is marked as FAILED
        assertEquals(OrderStatus.FAILED, order.status)
        assertNotNull(order.failedReason)
        assertTrue(order.failedReason!!.contains("Insufficient balance"))

        // Verify the order status is returned correctly
        val orderStatusResponse = exchangeService.orderStatus(currencyPairSymbol, orderId)
        assertEquals(1, orderStatusResponse.size)
        val response = orderStatusResponse[0]
        assertEquals(OrderStatus.FAILED, response.orderStatusType)
        assertEquals(orderId, response.orderId)
        assertEquals(currencyPairSymbol, response.currencyPair)
    }

    @Test
    fun `test place sell order with insufficient base currency results in failed order`() {
        val userId = "user2"
        val orderId = UUID.randomUUID().toString()
        val currencyPairSymbol = "BTCUSDC"
        val baseCurrency = "BTC"
        val quoteCurrency = "USDC"
        val price = BigDecimal("20000.00")
        val quantity = BigDecimal("1.0")

        // Mock User with insufficient BTC balance
        val user = User(
            id = userId,
            email = "test2@example.com",
            wallet = Wallet(
                baseBalances = mutableMapOf(baseCurrency to BigDecimal("0.5")),
                quoteBalances = mutableMapOf(quoteCurrency to BigDecimal("50000.00"))
            )
        )

        `when`(userService.get(userId)).thenReturn(user)

        // Create a Sell Order
        val order = Order(
            id = orderId,
            price = price,
            quantity = quantity,
            side = OrderSide.SELL,
            pair = currencyPairSymbol,
            user = userId,
            status = OrderStatus.OPEN,
            filledQuantity = BigDecimal.ZERO,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Place the Sell Order
        exchangeService.placeOrder(order)

        // Verify the order is marked as FAILED
        assertEquals(OrderStatus.FAILED, order.status)
        assertNotNull(order.failedReason)
        assertTrue(order.failedReason!!.contains("Insufficient balance"))

        // Verify the order status is returned correctly
        val orderStatusResponse = exchangeService.orderStatus(currencyPairSymbol, orderId)
        assertEquals(1, orderStatusResponse.size)
        val response = orderStatusResponse[0]
        assertEquals(OrderStatus.FAILED, response.orderStatusType)
        assertEquals(orderId, response.orderId)
        assertEquals(currencyPairSymbol, response.currencyPair)
    }

    @Test
    fun `test order status for an OPEN order`() {
        val userId = "user1"
        val orderId = UUID.randomUUID().toString()
        val currencyPairSymbol = "BTCUSDC"

        // Mock user
        val user = User(id = userId, wallet = Wallet(quoteBalances = mutableMapOf("USDC" to BigDecimal("50000.00"))))
        `when`(userService.get(userId)).thenReturn(user)

        val order = Order(
            id = orderId,
            price = BigDecimal("20000.00"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.BUY,
            pair = currencyPairSymbol,
            user = userId,
            status = OrderStatus.OPEN,
            filledQuantity = BigDecimal.ZERO,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Simulate placing the order
        exchangeService.placeOrder(order)

        // Test order status
        val orderStatusResponse = exchangeService.orderStatus(currencyPairSymbol, orderId)

        assertEquals(1, orderStatusResponse.size)
        val response = orderStatusResponse[0]
        assertEquals(OrderStatus.OPEN, response.orderStatusType)
        assertEquals(orderId, response.orderId)
        assertEquals(currencyPairSymbol, response.currencyPair)
    }

    @Test
    fun `test order status for a PARTIALLY_FILLED order`() {
        val userId = "user2"
        val orderId = UUID.randomUUID().toString()
        val currencyPairSymbol = "BTCUSDC"

        // Mock user
        val user = User(id = userId, wallet = Wallet(baseBalances = mutableMapOf("BTC" to BigDecimal("2.0"))))
        `when`(userService.get(userId)).thenReturn(user)

        val order = Order(
            id = orderId,
            price = BigDecimal("20000.00"),
            quantity = BigDecimal("2.0"),
            side = OrderSide.SELL,
            pair = currencyPairSymbol,
            user = userId,
            status = OrderStatus.PARTIALLY_FILLED,
            filledQuantity = BigDecimal("1.0"),
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Add the order manually to the allOrders map
        exchangeService.placeOrder(order)

        // Test order status
        val orderStatusResponse = exchangeService.orderStatus(currencyPairSymbol, orderId)

        assertEquals(1, orderStatusResponse.size)
        val response = orderStatusResponse[0]
        assertEquals(OrderStatus.PARTIALLY_FILLED, response.orderStatusType)
        assertEquals(orderId, response.orderId)
        assertEquals(currencyPairSymbol, response.currencyPair)
    }

    @Test
    fun `test order status for a FILLED order`() {
        val userId = "user3"
        val orderId = UUID.randomUUID().toString()
        val currencyPairSymbol = "BTCUSDC"

        // Mock user
        val user = User(id = userId, wallet = Wallet(baseBalances = mutableMapOf("BTC" to BigDecimal("1.0"))))
        `when`(userService.get(userId)).thenReturn(user)

        val order = Order(
            id = orderId,
            price = BigDecimal("20000.00"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.SELL,
            pair = currencyPairSymbol,
            user = userId,
            status = OrderStatus.FILLED,
            filledQuantity = BigDecimal("1.0"),
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Add the order manually to the allOrders map
        exchangeService.placeOrder(order)

        // Test order status
        val orderStatusResponse = exchangeService.orderStatus(currencyPairSymbol, orderId)

        assertEquals(1, orderStatusResponse.size)
        val response = orderStatusResponse[0]
        assertEquals(OrderStatus.FILLED, response.orderStatusType)
        assertEquals(orderId, response.orderId)
        assertEquals(currencyPairSymbol, response.currencyPair)
    }

    @Test
    fun `test order status for a CANCELLED order`() {
        val userId = "user4"
        val orderId = UUID.randomUUID().toString()
        val currencyPairSymbol = "BTCUSDC"

        // Mock user
        val user = User(id = userId, wallet = Wallet(quoteBalances = mutableMapOf("USDC" to BigDecimal("20000.00"))))
        `when`(userService.get(userId)).thenReturn(user)

        val order = Order(
            id = orderId,
            price = BigDecimal("20000.00"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.BUY,
            pair = currencyPairSymbol,
            user = userId,
            status = OrderStatus.OPEN,
            filledQuantity = BigDecimal.ZERO,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Simulate placing the order
        exchangeService.placeOrder(order)

        // Cancel the order
        exchangeService.cancelOrder(orderId, currencyPairSymbol)

        // Test order status
        val orderStatusResponse = exchangeService.orderStatus(currencyPairSymbol, orderId)

        assertEquals(1, orderStatusResponse.size)
        val response = orderStatusResponse[0]
        assertEquals(OrderStatus.CANCELLED, response.orderStatusType)
        assertEquals(orderId, response.orderId)
        assertEquals(currencyPairSymbol, response.currencyPair)
    }
}
