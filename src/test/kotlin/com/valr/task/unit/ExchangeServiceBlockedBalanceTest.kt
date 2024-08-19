package com.valr.task.unit

import com.valr.task.domain.*
import com.valr.task.service.CurrencyPairService
import com.valr.task.service.ExchangeService
import com.valr.task.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.*

class ExchangeServiceBlockedBalanceTest {

    private lateinit var userService: UserService
    private lateinit var currencyPairService: CurrencyPairService
    private lateinit var exchangeService: ExchangeService
    private lateinit var userBuyer: User
    private lateinit var userSeller: User
    private lateinit var currencyPair: CurrencyPair

    @BeforeEach
    fun setup() {
        userService = mock()
        currencyPairService = mock()
        exchangeService = ExchangeService(userService, currencyPairService, BigDecimal("0.01"), BigDecimal("0.02"))

        // Set up test users
        userBuyer = User(
            id = "buyer", wallet = Wallet(
                baseBalances = mutableMapOf("BTC" to BigDecimal("10.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("1000.0"))
            )
        )

        userSeller = User(
            id = "seller", wallet = Wallet(
                baseBalances = mutableMapOf("BTC" to BigDecimal("5.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("0.0"))
            )
        )

        currencyPair =
            CurrencyPair(
                symbol = "BTCUSDC",
                baseCurrency = "BTC",
                quoteCurrency = "USDC",
                shortName = "BTC/USDC",
                baseDecimalPlaces = 2
            )

        whenever(currencyPairService.getAllPairs()).thenReturn(mutableMapOf(currencyPair.symbol to currencyPair))
        whenever(userService.get(userBuyer.id)).thenReturn(userBuyer)
        whenever(userService.get(userSeller.id)).thenReturn(userSeller)
    }

    @Test
    fun `placeOrder should block funds correctly for a BUY order`() {
        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            user = userBuyer.id,
            pair = currencyPair.symbol,
            price = BigDecimal("50.0"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.BUY
        )

        // When
        exchangeService.placeOrder(buyOrder)

        // Then
        assertTrue(BigDecimal("50.0").compareTo(userBuyer.wallet.blockedQuoteBalances[currencyPair.quoteCurrency]) == 0)
        verify(userService).update(userBuyer)
    }

    @Test
    fun `placeOrder should block funds correctly for a SELL order`() {
        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            user = userSeller.id,
            pair = currencyPair.symbol,
            price = BigDecimal("50.0"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.SELL
        )

        // When
        exchangeService.placeOrder(sellOrder)

        // Then
        assertEquals(BigDecimal("1.0"), userSeller.wallet.blockedBaseBalances[currencyPair.baseCurrency])
        verify(userService).update(userSeller)
    }

    @Test
    fun `cancelOrder should release blocked funds correctly for a BUY order`() {
        val buyOrder = Order(
            id = UUID.randomUUID().toString(),
            user = userBuyer.id,
            pair = currencyPair.symbol,
            price = BigDecimal("50.0"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.BUY
        )
        exchangeService.placeOrder(buyOrder)

        // Simulate order cancellation
        exchangeService.cancelOrder(buyOrder.id, currencyPair.symbol)

        // Then
        assertTrue(BigDecimal.ZERO.compareTo(userBuyer.wallet.blockedQuoteBalances[currencyPair.quoteCurrency]) == 0)
        assertTrue(BigDecimal("1000.0").plus(BigDecimal("50.0")).compareTo(userBuyer.wallet.quoteBalances[currencyPair.quoteCurrency]) == 0)
    }

    @Test
    fun `cancelOrder should release blocked funds correctly for a SELL order`() {
        val sellOrder = Order(
            id = UUID.randomUUID().toString(),
            user = userSeller.id,
            pair = currencyPair.symbol,
            price = BigDecimal("50.0"),
            quantity = BigDecimal("1.0"),
            side = OrderSide.SELL
        )
        exchangeService.placeOrder(sellOrder)

        // Simulate order cancellation
        exchangeService.cancelOrder(sellOrder.id, currencyPair.symbol)

        // Then
        assertTrue(BigDecimal.ZERO.compareTo(userSeller.wallet.blockedBaseBalances[currencyPair.baseCurrency]) == 0)
        assertEquals(BigDecimal("5.0") + BigDecimal("1.0"), userSeller.wallet.baseBalances[currencyPair.baseCurrency])
    }
}
