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
class ExchangeServiceOrderMatchingTest {

    private lateinit var exchangeService: ExchangeService

    @Mock
    private lateinit var currencyPairService: CurrencyPairService
    private lateinit var buyer: User
    private lateinit var seller: User
    private lateinit var buyerWallet: Wallet
    private lateinit var sellerWallet: Wallet

    @BeforeEach
    fun setup() {

        buyerWallet = Wallet(
            baseBalances = mutableMapOf("BTC" to BigDecimal("0.0")),
            quoteBalances = mutableMapOf("USDC" to BigDecimal("10000"))
        )
        sellerWallet = Wallet(
            baseBalances = mutableMapOf("BTC" to BigDecimal("1.0")),
            quoteBalances = mutableMapOf("USDC" to BigDecimal("0.0"))
        )

        buyer = User(id = "buyer", wallet = buyerWallet)
        seller = User(id = "seller", wallet = sellerWallet)

        exchangeService = ExchangeService(
            UserService(
                mutableMapOf(
                    "buyer" to buyer,
                    "seller" to seller,
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
    fun `test full match of a buy order`() {
        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("1.0"),
            user = seller.id,
            side = OrderSide.SELL
        )
        exchangeService.placeOrder(sellOrder)

        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("1.0"),
            user = buyer.id,
            side = OrderSide.BUY
        )

        exchangeService.placeOrder(buyOrder)

        assertEquals(OrderStatus.FILLED, buyOrder.status)
        assertEquals(OrderStatus.FILLED, sellOrder.status)

        assertEquals(BigDecimal("1.0"), buyer.wallet.baseBalances["BTC"])
        assertEquals(BigDecimal("2000.0"), seller.wallet.quoteBalances["USDC"])

        val orderBook = exchangeService.orderBook("BTCUSDC")
        assertTrue(orderBook.asks.isEmpty())
    }

    @Test
    fun `test partial match of a buy order`() {
        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("0.5"),
            user = seller.id,
            side = OrderSide.SELL
        )
        exchangeService.placeOrder(sellOrder)

        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("1.0"),
            user = buyer.id,
            side = OrderSide.BUY
        )
        exchangeService.placeOrder(buyOrder)

        assertEquals(OrderStatus.PARTIALLY_FILLED, buyOrder.status)
        assertEquals(OrderStatus.FILLED, sellOrder.status)

        assertEquals(BigDecimal("0.5"), buyer.wallet.baseBalances["BTC"])
        assertEquals(BigDecimal("1000.0"), seller.wallet.quoteBalances["USDC"])

        val orderBook = exchangeService.orderBook("BTCUSDC")
        assertEquals(1, orderBook.asks.size)

        val remainingBuyOrder = orderBook.asks[0]
        assertEquals(BigDecimal("2000.0"), remainingBuyOrder.price)
        assertEquals(BigDecimal("0.5"), remainingBuyOrder.quantity)
    }

    @Test
    fun `test full match of a sell order`() {
        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("1.0"),
            user = buyer.id,
            side = OrderSide.BUY
        )
        exchangeService.placeOrder(buyOrder)

        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("1.0"),
            user = seller.id,
            side = OrderSide.SELL
        )
        exchangeService.placeOrder(sellOrder)

        assertEquals(OrderStatus.FILLED, sellOrder.status)
        assertEquals(OrderStatus.FILLED, buyOrder.status)

        assertTrue(BigDecimal.ZERO.compareTo(seller.wallet.baseBalances["BTC"]) == 0)
        assertEquals(BigDecimal("2000.0"), seller.wallet.quoteBalances["USDC"])

        val orderBook = exchangeService.orderBook("BTCUSDC")
        assertTrue(orderBook.asks.isEmpty())
    }

    @Test
    fun `test partial match of a sell order`() {
        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("0.5"),
            user = buyer.id,
            side = OrderSide.BUY
        )
        exchangeService.placeOrder(buyOrder)

        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            pair = "BTCUSDC",
            price = BigDecimal("2000.0"),
            quantity = BigDecimal("1.0"),
            user = seller.id,
            side = OrderSide.SELL
        )
        exchangeService.placeOrder(sellOrder)

        assertEquals(OrderStatus.PARTIALLY_FILLED, sellOrder.status)
        assertEquals(OrderStatus.FILLED, buyOrder.status)

        assertEquals(BigDecimal("0.0"), seller.wallet.baseBalances["BTC"])
        assertEquals(BigDecimal("1000.0"), seller.wallet.quoteBalances["USDC"])

        val orderBook = exchangeService.orderBook("BTCUSDC")
        assertEquals(1, orderBook.asks.size)

        val remainingSellOrder = orderBook.asks[0]
        assertEquals(BigDecimal("2000.0"), remainingSellOrder.price)
        assertEquals(BigDecimal("0.5"), remainingSellOrder.quantity)
    }
}
