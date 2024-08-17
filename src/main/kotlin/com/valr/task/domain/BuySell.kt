package com.valr.task.domain

import java.math.BigDecimal
import java.util.*

data class CurrencyPair(
    val symbol: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val shortName: String,
    val baseDecimalPlaces: Int
)

enum class OrderSide { BUY, SELL }
enum class OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }

data class Order(
    val id: String = UUID.randomUUID().toString(),
    val price: BigDecimal,
    var quantity: BigDecimal,
    val side: OrderSide,
    var filledQuantity: BigDecimal = BigDecimal.ZERO,
    var status: OrderStatus = OrderStatus.OPEN,
    val user: String,
    val pair: String
) {
    val remainingQuantity: BigDecimal
        get() = quantity - filledQuantity

    fun updateStatus() {
        status = when {
            remainingQuantity.compareTo(BigDecimal.ZERO) == 0 -> OrderStatus.FILLED
            remainingQuantity < quantity -> OrderStatus.PARTIALLY_FILLED
            else -> OrderStatus.OPEN
        }
    }
}

data class OrderSummary(
    val side: OrderSide,
    var quantity: String,
    val price: String,
    val currencyPair: String,
    var orderCount: Int
)

data class OrderBookResponse(
    val asks: List<OrderSummary>,
    val lastChange: String,
    val sequenceNumber: Long
)

data class Trade(
    val price: String,
    val quantity: String,
    val currencyPair: String,
    val tradedAt: String,
    val takerSide: OrderSide,
    val sequenceId: Long,
    val id: String,
    val quoteVolume: String
)