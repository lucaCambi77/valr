package com.valr.task.service

import com.valr.task.domain.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicLong

@Service
class ExchangeService(

    private val userService: UserService,
    private val currencyPairService: CurrencyPairService,
    @Value("\${transaction.fee}") private val transactionFee: BigDecimal,
    @Value("\${maker.reward}") private val makerReward: BigDecimal
) {
    private val buyOrders: MutableMap<String, PriorityQueue<Order>> = mutableMapOf()
    private val sellOrders: MutableMap<String, PriorityQueue<Order>> = mutableMapOf()
    private val trades: MutableList<Trade> = mutableListOf()
    private val orderBookSequence = AtomicLong(0)
    private val tradeSequence = AtomicLong(0)

    fun orderBook(pair: String): OrderBookResponse {
        val currencyPair: CurrencyPair = getCurrencyPair(pair)

        val asks = aggregateOrders(sellOrders[currencyPair.symbol] ?: PriorityQueue(), OrderSide.SELL, currencyPair)
        val bids = aggregateOrders(buyOrders[currencyPair.symbol] ?: PriorityQueue(), OrderSide.BUY, currencyPair)

        val allOrders = asks + bids

        return OrderBookResponse(
            asks = allOrders,
            lastChange = LocalDateTime.now().toString(),
            sequenceNumber = orderBookSequence.get()
        )
    }

    private fun aggregateOrders(
        orders: PriorityQueue<Order>,
        side: OrderSide,
        currencyPair: CurrencyPair
    ): List<OrderSummary> {
        val orderSummaryMap: MutableMap<BigDecimal, OrderSummary> = mutableMapOf()
        for (order in orders) {
            val key = order.price
            val existingSummary = orderSummaryMap[key]
            if (existingSummary != null) {
                existingSummary.quantity = (BigDecimal(existingSummary.quantity) + order.remainingQuantity).toString()
                existingSummary.orderCount += 1
            } else {
                orderSummaryMap[key] = OrderSummary(
                    price = order.price.toString(),
                    quantity = order.remainingQuantity.toString(),
                    orderCount = 1,
                    side = side,
                    currencyPair = currencyPair.shortName
                )
            }
        }
        return orderSummaryMap.values.toList()
    }

    fun placeOrder(order: Order): String {
        val user = userService.get(order.user)

        val currencyPair = getCurrencyPair(order.pair)

        if (order.side == OrderSide.BUY) {
            val totalCost = order.quantity * order.price
            val balance = user.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO
            if (balance < totalCost) throw Exception("Insufficient balance for user ${user.id} to place order.")
            user.wallet.quoteBalances[currencyPair.quoteCurrency] = balance - totalCost
        } else {
            val balance = user.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO
            if (balance < order.quantity) throw Exception("Insufficient balance for user ${user.id} to place order.")
            user.wallet.baseBalances[currencyPair.baseCurrency] = balance - order.quantity
        }

        val buyQueue = buyOrders.getOrPut(currencyPair.symbol) { PriorityQueue(compareByDescending<Order> { it.price }) }
        val sellQueue = sellOrders.getOrPut(currencyPair.symbol) { PriorityQueue(compareBy<Order> { it.price }) }

        if (order.side == OrderSide.BUY) {
            matchOrder(order, sellQueue, buyQueue, currencyPair)
        } else {
            matchOrder(order, buyQueue, sellQueue, currencyPair)
        }

        orderBookSequence.incrementAndGet()

        return order.id
    }

    private fun matchOrder(
        buyOrSellOrder: Order,
        oppositeOrders: PriorityQueue<Order>,
        sameSideOrders: PriorityQueue<Order>,
        currencyPair: CurrencyPair,
    ) {
        val iterator = oppositeOrders.iterator()

        while (iterator.hasNext()) {
            val potentialMatch = iterator.next()
            if ((buyOrSellOrder.side == OrderSide.BUY && buyOrSellOrder.price >= potentialMatch.price) ||
                (buyOrSellOrder.side == OrderSide.SELL && buyOrSellOrder.price <= potentialMatch.price)
            ) {
                val tradeQuantity = minOf(buyOrSellOrder.remainingQuantity, potentialMatch.remainingQuantity)

                applyTrade(buyOrSellOrder, potentialMatch, potentialMatch.price, tradeQuantity, currencyPair)

                potentialMatch.filledQuantity += tradeQuantity
                potentialMatch.updateStatus()
                if (potentialMatch.status == OrderStatus.FILLED) {
                    iterator.remove()
                }

                buyOrSellOrder.filledQuantity += tradeQuantity
                buyOrSellOrder.updateStatus()
                if (buyOrSellOrder.status == OrderStatus.FILLED) {
                    break
                }

            } else {
                break
            }
        }

        if (buyOrSellOrder.status != OrderStatus.FILLED) {
            sameSideOrders.add(buyOrSellOrder)
        }
    }

    private fun applyTrade(buyOrSellOrder: Order, matchedOrder: Order, tradePrice: BigDecimal, tradeQuantity: BigDecimal, currencyPair: CurrencyPair) {
        val buyer: User
        val seller: User
        val makerOrder: Order = matchedOrder // The matched order is already in the order book, hence it's the Maker
        val takerOrder: Order = buyOrSellOrder // The incoming order is the Taker

        if (buyOrSellOrder.side == OrderSide.BUY) {
            buyer = userService.get(buyOrSellOrder.user)
            seller = userService.get(matchedOrder.user)
        } else {
            seller = userService.get(buyOrSellOrder.user)
            buyer = userService.get(matchedOrder.user)
        }

        // Calculate the Maker reward and Taker fee
        val makerRewardAmount: BigDecimal
        val takerFeeAmount: BigDecimal

        if (makerOrder.side == OrderSide.BUY) {
            makerRewardAmount = tradeQuantity * tradePrice * makerReward
            buyer.wallet.baseBalances[currencyPair.baseCurrency] = (buyer.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) + makerRewardAmount
        } else {
            makerRewardAmount = tradeQuantity * makerReward
            seller.wallet.quoteBalances[currencyPair.quoteCurrency] = (seller.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) + makerRewardAmount
        }

        if (takerOrder.side == OrderSide.BUY) {
            takerFeeAmount = tradeQuantity * transactionFee
            buyer.wallet.baseBalances[currencyPair.baseCurrency] = (buyer.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) + tradeQuantity - takerFeeAmount
        } else {
            takerFeeAmount = tradeQuantity * tradePrice * transactionFee
            seller.wallet.quoteBalances[currencyPair.quoteCurrency] = (seller.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) + tradeQuantity * tradePrice - takerFeeAmount
        }

        userService.update(buyer)
        userService.update(seller)

        trades.add(
            Trade(
                price = tradePrice.toString(),
                quantity = tradeQuantity.toString(),
                currencyPair = currencyPair.symbol,
                tradedAt = Instant.now().toString(),
                takerSide = buyOrSellOrder.side,
                sequenceId = tradeSequence.incrementAndGet(),
                id = UUID.randomUUID().toString(),
                quoteVolume = (tradeQuantity * tradePrice).toString()
            )
        )
    }

    fun cancelOrder(orderId: String, pair: String) {
        val currencyPair = getCurrencyPair(pair)

        val allOrders = buyOrders.values.flatten() + sellOrders.values.flatten()
        val order = allOrders.find { it.id == orderId }

        if (order == null) {
            throw Exception("Order $orderId does not exists or it has already been fulfilled")
        }

        order.status = OrderStatus.CANCELLED

        val user = userService.get(order.user)

        if (order.side == OrderSide.BUY) {
            val balance = user.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO
            user.wallet.quoteBalances[currencyPair.quoteCurrency] = balance + (order.remainingQuantity * order.price)
        } else {
            val balance = user.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO
            user.wallet.baseBalances[currencyPair.baseCurrency] = balance + order.remainingQuantity
        }

        userService.update(user)

        orderBookSequence.incrementAndGet()
    }

    fun getRecentTrades(currencyPair: String, limit: Int): List<Trade> {
        return trades.filter { it.currencyPair == currencyPair }
            .sortedByDescending { it.tradedAt }
            .take(limit)
    }

    fun getCurrencyPair(pair: String): CurrencyPair = currencyPairService.getAllPairs()[pair] ?: throw Exception("Currency pair $pair does not exists or it is not supported")

    fun applyDecimalScale(decimalScale: Int, value: BigDecimal): BigDecimal {
        return value.setScale(decimalScale, RoundingMode.DOWN)
    }
}
