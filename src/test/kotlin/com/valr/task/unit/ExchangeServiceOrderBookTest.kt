package com.valr.task.unit

import com.valr.task.domain.*
import com.valr.task.service.CurrencyPairService
import com.valr.task.service.ExchangeService
import com.valr.task.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
class ExchangeServiceOrderBookTest {

    private lateinit var exchangeService: ExchangeService
    @Mock
    private lateinit var currencyPairService: CurrencyPairService
    private lateinit var user: User
    private lateinit var wallet: Wallet

    @BeforeEach
    fun setup() {
        wallet = Wallet(
            baseBalances = mutableMapOf("BTC" to BigDecimal("1.0")),
            quoteBalances = mutableMapOf("USDC" to BigDecimal("10000"))
        )
        user = User(id = "user", wallet = wallet)

        exchangeService = ExchangeService(
            UserService(
                mutableMapOf(
                    "user" to user
                )
            ), currencyPairService, BigDecimal("0.01"), BigDecimal("0.001")
        )

        `when`(currencyPairService.getAllPairs()).thenReturn(
            mutableMapOf(
                "BTCUSDC" to
                        CurrencyPair(
                            symbol = "BTCUSDC",
                            baseCurrency = "BTC",
                            quoteCurrency = "USDC",
                            shortName = "BTC/USDC",
                            baseDecimalPlaces = 2
                        )
            )
        )
    }

    @Test
    fun `test orderBook returns empty order book`() {
        val orderBook = exchangeService.orderBook("BTCUSDC")
        assertTrue(orderBook.asks.isEmpty())
        assertEquals(0L, orderBook.sequenceNumber)
    }

    @Test
    fun `test orderBook returns order book with only buy orders`() {
        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("1000"),
            quantity = BigDecimal("0.1"),
            user = user.id,
            side = OrderSide.BUY
        )

        exchangeService.placeOrder(buyOrder)
        val orderBook = exchangeService.orderBook("BTCUSDC")

        assertEquals(1, orderBook.asks.size)

        val bidOrder = orderBook.asks[0]
        assertEquals(buyOrder.price.toString(), bidOrder.price)
        assertEquals(buyOrder.quantity.toString(), bidOrder.quantity)
    }

    @Test
    fun `test orderBook returns order book with only sell orders`() {
        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000"),
            quantity = BigDecimal("0.2"),
            user = user.id,
            side = OrderSide.SELL
        )

        exchangeService.placeOrder(sellOrder)
        val orderBook = exchangeService.orderBook("BTCUSDC")

        assertEquals(1, orderBook.asks.size)

        val askOrder = orderBook.asks[0]
        assertEquals(sellOrder.price.toString(), askOrder.price)
        assertEquals(sellOrder.quantity.toString(), askOrder.quantity)
    }

    @Test
    fun `test orderBook returns order book with both buy and sell orders`() {
        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("1000"),
            quantity = BigDecimal("0.1"),
            user = user.id,
            side = OrderSide.BUY
        )

        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000"),
            quantity = BigDecimal("0.2"),
            user = user.id,
            side = OrderSide.SELL
        )

        exchangeService.placeOrder(buyOrder)
        exchangeService.placeOrder(sellOrder)
        val orderBook = exchangeService.orderBook("BTCUSDC")

        assertEquals(2, orderBook.asks.size)

        val bidOrder = orderBook.asks[0]
        val askOrder = orderBook.asks[1]

        assertEquals(sellOrder.price.toString(), bidOrder.price)
        assertEquals(sellOrder.quantity.toString(), bidOrder.quantity)
        assertEquals(buyOrder.price.toString(), askOrder.price)
        assertEquals(buyOrder.quantity.toString(), askOrder.quantity)
    }

    @Test
    fun `test orderBook aggregates orders with the same price`() {
        val buyOrder1 = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("1000"),
            quantity = BigDecimal("0.1"),
            user = user.id,
            side = OrderSide.BUY
        )

        val buyOrder2 = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("1000"),
            quantity = BigDecimal("0.2"),
            user = user.id,
            side = OrderSide.BUY
        )

        exchangeService.placeOrder(buyOrder1)
        exchangeService.placeOrder(buyOrder2)
        val orderBook = exchangeService.orderBook("BTCUSDC")

        assertEquals(1, orderBook.asks.size)

        val aggregatedBidOrder = orderBook.asks[0]
        assertEquals(buyOrder1.price.toString(), aggregatedBidOrder.price)
        assertEquals("0.3", aggregatedBidOrder.quantity)
        assertEquals(2, aggregatedBidOrder.orderCount)
    }

    @Test
    fun `test orderBook correctly sequences orders`() {
        val buyOrder1 = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("1000"),
            quantity = BigDecimal("0.1"),
            user = user.id,
            side = OrderSide.BUY
        )

        val sellOrder1 = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000"),
            quantity = BigDecimal("0.2"),
            user = user.id,
            side = OrderSide.SELL
        )

        exchangeService.placeOrder(buyOrder1)
        exchangeService.placeOrder(sellOrder1)

        val orderBook = exchangeService.orderBook("BTCUSDC")

        assertEquals(2L, orderBook.sequenceNumber)
    }
}

