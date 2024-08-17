package com.valr.task.unit

import com.valr.task.domain.*
import com.valr.task.service.CurrencyPairService
import com.valr.task.service.ExchangeService
import com.valr.task.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.util.*

class ExchangeServiceCancelTest {

    private lateinit var userService: UserService
    private lateinit var currencyPairService: CurrencyPairService
    private lateinit var exchangeService: ExchangeService

    @BeforeEach
    fun setup() {
        userService = mock(UserService::class.java)
        currencyPairService = mock(CurrencyPairService::class.java)

        // Initializing ExchangeService with mocked dependencies and test values
        exchangeService = ExchangeService(
            userService,
            currencyPairService,
            transactionFee = BigDecimal("0.001"),
            makerReward = BigDecimal("0.0005")
        )

        // Mocking currency pair service and user service
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
    fun `cancelOrder should update order status and adjust user balance correctly for a buy order`() {
        // Arrange
        val userId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val order = Order(
            id = orderId,
            pair = "BTCUSDC",
            price = BigDecimal("50000"),
            quantity = BigDecimal("0.1"),
            user = userId,
            side = OrderSide.BUY,
            filledQuantity = BigDecimal("0.05")
        )

        val user = User(id = userId, wallet = Wallet(quoteBalances = mutableMapOf("USDC" to BigDecimal("100000.00"))))

        `when`(userService.get(userId)).thenReturn(user)

        exchangeService.placeOrder(order)

        // Act
        exchangeService.cancelOrder(orderId, "BTCUSDC")

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(BigDecimal("95000.00") + (order.remainingQuantity * order.price), user.wallet.quoteBalances["USDC"])

        // Verify that the userService.update() method was called with the updated user
        verify(userService).update(user)
    }

    @Test
    fun `cancelOrder should update order status and adjust user balance correctly for a sell order`() {
        // Arrange
        val userId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val order = Order(
            id = orderId,
            pair = "BTCUSDC",
            price = BigDecimal("50000"),
            quantity = BigDecimal("0.1"),
            user = userId,
            side = OrderSide.SELL,
            filledQuantity = BigDecimal("0.05")
        )

        val user = User(id = userId, wallet = Wallet(baseBalances = mutableMapOf("BTC" to BigDecimal("0.5"))))

        `when`(userService.get(userId)).thenReturn(user)

        exchangeService.placeOrder(order)

        // Act
        exchangeService.cancelOrder(orderId, "BTCUSDC")

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(BigDecimal("0.4") + order.remainingQuantity, user.wallet.baseBalances["BTC"])

        // Verify that the userService.update() method was called with the updated user
        verify(userService).update(user)
    }

    @Test
    fun `cancelOrder should throw exception for non-existent order`() {

        // Act & Assert
        val exception = assertThrows<Exception> {
            exchangeService.cancelOrder("non-existent-order-id", "BTCUSDC")
        }

        assertEquals("Order non-existent-order-id does not exists or it has already been fulfilled", exception.message)
    }
}

