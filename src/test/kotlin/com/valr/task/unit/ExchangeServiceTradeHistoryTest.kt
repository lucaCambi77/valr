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
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class ExchangeServiceTradeHistoryTest {

    private lateinit var exchangeService: ExchangeService

    @Mock
    private lateinit var currencyPairService: CurrencyPairService
    private lateinit var userService: UserService
    private lateinit var user1: User
    private lateinit var user2: User
    private lateinit var user3: User

    @BeforeEach
    fun setup() {
        userService = UserService()
        exchangeService = ExchangeService(
            userService, currencyPairService,
            BigDecimal("0.01"), BigDecimal("0.001")
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
                        ),
                "ETHUSDC" to
                        CurrencyPair(
                            symbol = "ETHUSDC",
                            baseCurrency = "ETH",
                            quoteCurrency = "USDC",
                            shortName = "ETH/USDC",
                            baseDecimalPlaces = 2
                        )
            )
        )

        user1 = User(
            id = "user1",
            name = "Alice",
            email = "alice@example.com",
            wallet = Wallet(
                baseBalances = mutableMapOf("BTC" to BigDecimal("1.0"), "ETH" to BigDecimal("5.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("50000.0"))
            )
        )

        user2 = User(
            id = "user2",
            name = "Bob",
            email = "bob@example.com",
            wallet = Wallet(
                baseBalances = mutableMapOf("BTC" to BigDecimal("1.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("50000.0"))
            )
        )

        user3 = User(
            id = "user3",
            name = "Charlie",
            email = "charlie@example.com",
            wallet = Wallet(
                baseBalances = mutableMapOf("ETH" to BigDecimal("5.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("50000.0"))
            )
        )

        userService.update(user1)
        userService.update(user2)
        userService.update(user3)

        // Place orders for BTCUSDC
        exchangeService.placeOrder(
            Order(
                id = UUID.randomUUID().toString(),
                user = user1.id,
                pair = "BTCUSDC",
                price = BigDecimal("20000.00"),
                quantity = BigDecimal("0.5"),
                side = OrderSide.BUY
            )
        )

        exchangeService.placeOrder(
            Order(
                id = UUID.randomUUID().toString(),
                user = user2.id,
                pair = "BTCUSDC",
                price = BigDecimal("20000.00"),
                quantity = BigDecimal("1.0"),
                side = OrderSide.SELL
            )
        )

        exchangeService.placeOrder(
            Order(
                id = UUID.randomUUID().toString(),
                user = user1.id,
                pair = "BTCUSDC",
                price = BigDecimal("20000.00"),
                quantity = BigDecimal("0.5"),
                side = OrderSide.BUY
            )
        )

        // Place orders for ETHUSDC
        exchangeService.placeOrder(
            Order(
                id = UUID.randomUUID().toString(),
                user = user1.id,
                pair = "ETHUSDC",
                price = BigDecimal("1500.00"),
                quantity = BigDecimal("2"),
                side = OrderSide.BUY
            )
        )
        exchangeService.placeOrder(
            Order(
                id = UUID.randomUUID().toString(),
                user = user3.id,
                pair = "ETHUSDC",
                price = BigDecimal("1500.00"),
                quantity = BigDecimal("2"),
                side = OrderSide.SELL
            )
        )
    }

    @Test
    fun `test recent trades for multiple pairs with limit`() {
        // Test for BTCUSDC
        val btcTrades = exchangeService.tradeHistory("BTCUSDC", 10)
        assertEquals(2, btcTrades.size)
        assertEquals("BTCUSDC", btcTrades[0].currencyPair)
        assertEquals(BigDecimal("20000.00"), BigDecimal(btcTrades[0].price))
        assertEquals(BigDecimal("0.5"), BigDecimal(btcTrades[0].quantity))

        assertEquals("BTCUSDC", btcTrades[1].currencyPair)
        assertEquals(BigDecimal("20000.00"), BigDecimal(btcTrades[1].price))
        assertEquals(BigDecimal("0.5"), BigDecimal(btcTrades[1].quantity))

        // Test for ETHUSDC
        val ethTrades = exchangeService.tradeHistory("ETHUSDC", 10)
        assertEquals(1, ethTrades.size)
        assertEquals("ETHUSDC", ethTrades[0].currencyPair)
        assertEquals(BigDecimal("1500.00"), BigDecimal(ethTrades[0].price))
        assertEquals(BigDecimal("2"), BigDecimal(ethTrades[0].quantity))
    }

    @Test
    fun `test trades sorted by trade time descending`() {
        val currencyPair = "BTCUSDC"
        val recentTrades = exchangeService.tradeHistory(currencyPair, 10)

        // Ensure trades are sorted by tradedAt descending
        val sortedTrades = recentTrades.sortedByDescending { Instant.parse(it.tradedAt) }
        assertEquals(recentTrades, sortedTrades)
    }

    @Test
    fun `test recent trades limit`() {
        // Limit the number of trades returned
        val currencyPair = "BTCUSDC"
        val limitedTrades = exchangeService.tradeHistory(currencyPair, 1)

        // Ensure that only the last trade is returned
        assertEquals(1, limitedTrades.size)
    }

    @Test
    fun `test recent trades include correct orders`() {
        val btcTrades = exchangeService.tradeHistory("BTCUSDC", 10)

        // Check the trade's details for BTCUSDC
        assertTrue(btcTrades.any { it.price == "20000.00" && it.quantity == "0.5" })
        assertEquals("BUY", btcTrades[0].takerSide.name)
        assertEquals("SELL", btcTrades[1].takerSide.name)

        val ethTrades = exchangeService.tradeHistory("ETHUSDC", 10)

        // Check the trade's details for ETHUSDC
        assertTrue(ethTrades.any { it.price == "1500.00" && it.quantity == "2" })
        assertEquals("SELL", ethTrades[0].takerSide.name)
    }

    @Test
    fun `test no trades returned for unknown currency pair`() {
        val trades = exchangeService.tradeHistory("LTCUSDC", 10)
        assertTrue(trades.isEmpty())
    }

}
