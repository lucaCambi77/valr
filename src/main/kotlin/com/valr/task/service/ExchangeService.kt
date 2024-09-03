package com.valr.task.service

import com.valr.task.domain.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
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
    private val allOrders: MutableMap<String, Order> = mutableMapOf()

    fun orderBook(pair: String): OrderBookResponse {
        val currencyPair: CurrencyPair = getCurrencyPair(pair)

        val asks = aggregateOrders(sellOrders[currencyPair.symbol] ?: PriorityQueue(), OrderSide.SELL, currencyPair)
        val bids = aggregateOrders(buyOrders[currencyPair.symbol] ?: PriorityQueue(), OrderSide.BUY, currencyPair)

        return OrderBookResponse(
            asks = asks,
            bids = bids,
            lastChange = Instant.now().toString(),
            sequenceNumber = orderBookSequence.get()
        )
    }

    private fun aggregateOrders(
        orders: PriorityQueue<Order>,
        side: OrderSide,
        currencyPair: CurrencyPair
    ): List<OrderSummary> {
        return orders.groupBy { it.price }
            .map { (price, orders) ->
                orders.fold(
                    OrderSummary(
                        price = price.toString(),
                        quantity = BigDecimal.ZERO.toString(),
                        orderCount = 0,
                        side = side,
                        currencyPair = currencyPair.shortName
                    )
                ) { summary, order ->
                    summary.copy(
                        quantity = (BigDecimal(summary.quantity) + order.remainingQuantity).toString(),
                        orderCount = summary.orderCount + 1
                    )
                }
            }
    }

    fun placeOrder(order: Order): String {
        val user = userService.get(order.user)
        val currencyPair = getCurrencyPair(order.pair)

        try {
            placeOrder(currencyPair, order, user)
        } catch (e: Exception) {
            order.status = OrderStatus.FAILED
            order.failedReason = e.message
            order.updatedAt = Instant.now().toString()
            releaseBlockedFunds(user, order, currencyPair)
        }

        orderBookSequence.incrementAndGet()

        allOrders[order.id] = order

        return order.id
    }

    private fun placeOrder(currencyPair: CurrencyPair, order: Order, user: User) {

        if (!hasBalance(user, order, currencyPair)) {
            throw Exception("Insufficient balance for user ${user.id} to place order.")
        }

        blockFunds(user, order, currencyPair)

        val buyQueue = buyOrders.getOrPut(currencyPair.symbol) { PriorityQueue(compareByDescending<Order> { it.price }) }
        val sellQueue = sellOrders.getOrPut(currencyPair.symbol) { PriorityQueue(compareBy<Order> { it.price }) }

        if (order.side == OrderSide.BUY) {
            matchOrder(order, sellQueue, buyQueue, currencyPair)
        } else {
            matchOrder(order, buyQueue, sellQueue, currencyPair)
        }
    }

    private fun hasBalance(user: User, order: Order, currencyPair: CurrencyPair): Boolean {
        val quoteBalance = user.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO
        val blockedQuoteBalance = user.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO
        val baseBalance = user.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO
        val blockedBaseBalance = user.wallet.blockedBaseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO

        return if (order.side == OrderSide.BUY) {
            val totalCost = order.quantity * order.price
            quoteBalance >= totalCost - blockedQuoteBalance
        } else {
            baseBalance >= order.quantity - blockedBaseBalance
        }
    }

    private fun blockFunds(user: User, order: Order, currencyPair: CurrencyPair) {
        if (order.side == OrderSide.BUY) {
            val totalCost = order.quantity * order.price
            user.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] =
                (user.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) + totalCost
        } else {
            user.wallet.blockedBaseBalances[currencyPair.baseCurrency] =
                (user.wallet.blockedBaseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) + order.quantity
        }
        userService.update(user)
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
                if (buyOrSellOrder.user == potentialMatch.user) {
                    buyOrSellOrder.failedReason = "We did not execute this order since it would have matched with your own order on the Exchange"
                    buyOrSellOrder.status = OrderStatus.FAILED
                    buyOrSellOrder.updatedAt = Instant.now().toString()
                    return
                }

                val tradeQuantity = minOf(buyOrSellOrder.remainingQuantity, potentialMatch.remainingQuantity)

                trade(buyOrSellOrder, potentialMatch, potentialMatch.price, tradeQuantity, currencyPair)

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

    private fun trade(buyOrSellOrder: Order, matchedOrder: Order, tradePrice: BigDecimal, tradeQuantity: BigDecimal, currencyPair: CurrencyPair) {
        val buyer: User
        val seller: User

        if (buyOrSellOrder.side == OrderSide.BUY) {
            buyer = userService.get(buyOrSellOrder.user)
            seller = userService.get(matchedOrder.user)
        } else {
            seller = userService.get(buyOrSellOrder.user)
            buyer = userService.get(matchedOrder.user)
        }

        // Calculate the Maker reward and Taker fee, adjust user balances and unblock balances
        val makerRewardAmount: BigDecimal
        val takerFeeAmount: BigDecimal

        if (buyOrSellOrder.side == OrderSide.BUY) {
            val totalCost = tradeQuantity * tradePrice

            // Seller is the Maker
            makerRewardAmount = tradeQuantity * makerReward
            seller.wallet.baseBalances[currencyPair.baseCurrency] =
                (seller.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) + makerRewardAmount - tradeQuantity

            // Seller increase the quote currency
            seller.wallet.quoteBalances[currencyPair.quoteCurrency] =
                (seller.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) + totalCost

            // Buyer is the Taker
            takerFeeAmount = tradeQuantity * transactionFee
            buyer.wallet.baseBalances[currencyPair.baseCurrency] =
                (buyer.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) + tradeQuantity - takerFeeAmount

            // Buyer decrease the quote currency
            buyer.wallet.quoteBalances[currencyPair.quoteCurrency] =
                (buyer.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) - totalCost

            // Adjust the buyer's blocked quote balance and actual balance
            buyer.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] =
                (buyer.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) - totalCost

            // Adjust the buyer's blocked quote balance and actual balance
            seller.wallet.blockedBaseBalances[currencyPair.baseCurrency] =
                (seller.wallet.blockedBaseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) - tradeQuantity
        } else {
            // Buyer is the Maker
            makerRewardAmount = tradeQuantity * tradePrice * makerReward
            buyer.wallet.quoteBalances[currencyPair.quoteCurrency] =
                (buyer.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) + makerRewardAmount - (tradeQuantity * tradePrice)

            // Buyer increase the base currency
            buyer.wallet.baseBalances[currencyPair.baseCurrency] =
                (buyer.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) + tradeQuantity

            // Seller is the Taker
            takerFeeAmount = tradeQuantity * tradePrice * transactionFee
            val netQuote = tradeQuantity * tradePrice - takerFeeAmount

            seller.wallet.quoteBalances[currencyPair.quoteCurrency] =
                (seller.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) + netQuote

            // Seller decrease the base currency
            seller.wallet.baseBalances[currencyPair.baseCurrency] =
                (seller.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) - tradeQuantity

            // Adjust the seller's blocked base balance
            seller.wallet.blockedBaseBalances[currencyPair.baseCurrency] =
                (seller.wallet.blockedBaseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO) - tradeQuantity

            // Adjust the buyer's blocked quote balance
            buyer.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] =
                (buyer.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO) - (tradeQuantity * tradePrice)
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

        buyOrSellOrder.updatedAt = Instant.now().toString()
        matchedOrder.updatedAt = Instant.now().toString()
    }

    fun orderStatus(currencyPair: String, orderId: String): List<OrderStatusResponse> {
        return allOrders.values.filter { order ->
            order.pair == currencyPair && order.id == orderId
        }.map { order ->
            OrderStatusResponse(
                orderId = order.id,
                orderStatusType = order.status,
                currencyPair = order.pair,
                originalPrice = order.price.toString(),
                remainingQuantity = order.remainingQuantity.toString(),
                originalQuantity = order.quantity.toString(),
                orderSide = order.side,
                orderType = order.orderType,
                failedReason = order.failedReason,
                orderUpdatedAt = order.updatedAt.toString(),
                orderCreatedAt = order.createdAt
            )
        }
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
        releaseBlockedFunds(user, order, currencyPair)

        if (order.side == OrderSide.BUY) {
            val balance = user.wallet.quoteBalances[currencyPair.quoteCurrency] ?: BigDecimal.ZERO
            user.wallet.quoteBalances[currencyPair.quoteCurrency] = balance + (order.remainingQuantity * order.price)
        } else {
            val balance = user.wallet.baseBalances[currencyPair.baseCurrency] ?: BigDecimal.ZERO
            user.wallet.baseBalances[currencyPair.baseCurrency] = balance + order.remainingQuantity
        }

        orderBookSequence.incrementAndGet()
    }

    fun tradeHistory(currencyPair: String, limit: Int): List<Trade> {
        return trades.filter { it.currencyPair == currencyPair }
            .sortedByDescending { it.tradedAt }
            .take(limit)
    }

    fun getCurrencyPair(pair: String): CurrencyPair = currencyPairService.getAllPairs()[pair] ?: throw Exception("Currency pair $pair does not exists or it is not supported")

    private fun releaseBlockedFunds(user: User, order: Order, currencyPair: CurrencyPair) {
        if (order.side == OrderSide.BUY) {
            val blockedAmount = order.quantity * order.price
            val currentBlockedQuoteBalance = user.wallet.blockedQuoteBalances.getOrDefault(currencyPair.quoteCurrency, BigDecimal.ZERO)
            user.wallet.blockedQuoteBalances[currencyPair.quoteCurrency] = currentBlockedQuoteBalance - blockedAmount
        } else {
            val currentBlockedBaseBalance = user.wallet.blockedBaseBalances.getOrDefault(currencyPair.baseCurrency, BigDecimal.ZERO)
            user.wallet.blockedBaseBalances[currencyPair.baseCurrency] = currentBlockedBaseBalance - order.quantity
        }

        userService.update(user)
    }
}
