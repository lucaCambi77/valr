package com.valr.task.unit

import com.valr.task.domain.*
import com.valr.task.service.CurrencyPairService
import com.valr.task.service.ExchangeService
import com.valr.task.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class ExchangeServiceFeeTest {

    private lateinit var exchangeService: ExchangeService
    @Mock
    private lateinit var currencyPairService: CurrencyPairService
    private lateinit var makerUser: User
    private lateinit var takerUser: User

    @BeforeEach
    fun setUp() {
        makerUser = User(
            id = "maker",
            wallet = Wallet(
                baseBalances = mutableMapOf("BTC" to BigDecimal("10.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("10000.0"))
            )
        )

        takerUser = User(
            id = "taker",
            wallet = Wallet(
                baseBalances = mutableMapOf("BTC" to BigDecimal("5.0")),
                quoteBalances = mutableMapOf("USDC" to BigDecimal("5000.0"))
            )
        )

        exchangeService = ExchangeService(
            UserService(
                mutableMapOf(
                    "maker" to makerUser,
                    "taker" to takerUser
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
    fun `test placeOrder with Maker selling BTC and Taker buying BTC`() {
        val makerOrder = Order(
            id = "maker-order-1",
            user = makerUser.id,
            pair = "BTCUSDC",
            side = OrderSide.SELL,
            price = BigDecimal("1000.0"),
            quantity = BigDecimal("1.0")
        )

        val takerOrder = Order(
            id = "taker-order-1",
            user = takerUser.id,
            pair = "BTCUSDC",
            side = OrderSide.BUY,
            price = BigDecimal("1000.0"),
            quantity = BigDecimal("1.0")
        )

        exchangeService.placeOrder(makerOrder)
        exchangeService.placeOrder(takerOrder)

        val expectedMakerBTCBalance = BigDecimal("9.0010")
        val expectedTakerBTCBalance = BigDecimal("5.990")

        val expectedMakerUSDCBalance = BigDecimal("11000.0")
        val expectedTakerUSDCBalance = BigDecimal("4000.0")

        assertEquals(expectedMakerBTCBalance, makerUser.wallet.baseBalances["BTC"])
        assertEquals(expectedTakerBTCBalance, takerUser.wallet.baseBalances["BTC"])
        assertEquals(expectedMakerUSDCBalance, makerUser.wallet.quoteBalances["USDC"])
        assertEquals(expectedTakerUSDCBalance, takerUser.wallet.quoteBalances["USDC"])
    }

    @Test
    fun `test placeOrder with Maker buying BTC and Taker selling BTC`() {
        val makerOrder = Order(
            id = "maker-order-2",
            user = makerUser.id,
            pair = "BTCUSDC",
            side = OrderSide.BUY,
            price = BigDecimal("1000.0"),
            quantity = BigDecimal("1.0")
        )

        val takerOrder = Order(
            id = "taker-order-2",
            user = takerUser.id,
            pair = "BTCUSDC",
            side = OrderSide.SELL,
            price = BigDecimal("1000.0"),
            quantity = BigDecimal("1.0")
        )

        exchangeService.placeOrder(makerOrder)
        exchangeService.placeOrder(takerOrder)

        val expectedMakerBTCBalance = BigDecimal("11.0")
        val expectedTakerBTCBalance = BigDecimal("4.0")

        val expectedMakerUSDCBalance = BigDecimal("9001.0")
        val expectedTakerUSDCBalance = BigDecimal("5999.0")

        assertEquals(expectedMakerBTCBalance, makerUser.wallet.baseBalances["BTC"])
        assertEquals(expectedTakerBTCBalance, takerUser.wallet.baseBalances["BTC"])
        assertEquals(expectedMakerUSDCBalance, makerUser.wallet.quoteBalances["USDC"])
        assertEquals(expectedTakerUSDCBalance, takerUser.wallet.quoteBalances["USDC"])
    }
}
