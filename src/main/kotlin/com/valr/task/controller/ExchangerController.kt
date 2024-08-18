package com.valr.task.controller

import com.valr.task.domain.*
import com.valr.task.service.ExchangeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.*

@RestController
@RequestMapping
class ExchangeController(val exchangeService: ExchangeService) {

    @GetMapping("/{currencyPair}/orderbook")
    fun orderBook(@PathVariable currencyPair: String): ResponseEntity<OrderBookResponse> {
        return ResponseEntity.ok(exchangeService.orderBook(currencyPair))
    }

    @GetMapping("/{currencyPair}/tradehistory")
    fun tradeHistory(
        @PathVariable currencyPair: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<Trade>> {
        return ResponseEntity.ok(exchangeService.tradeHistory(currencyPair, limit))
    }

    @PostMapping("/orders/limit")
    fun placeLimitOrder(
        @RequestBody orderRequest: OrderRequest, authentication: Authentication
    ): ResponseEntity<String> {

        val order = Order(
            id = orderRequest.id,
            user = authentication.name,
            pair = orderRequest.pair,
            price = BigDecimal(orderRequest.price),
            quantity = BigDecimal(orderRequest.quantity),
            side = orderRequest.side
        )

        return ResponseEntity(exchangeService.placeOrder(order), HttpStatus.ACCEPTED)
    }

    data class OrderRequest(
        val id: String = UUID.randomUUID().toString(),
        val price: String,
        var quantity: String,
        val side: OrderSide,
        val pair: String
    )

    @GetMapping("/orders/{currencyPair}/status/{orderId}")
    fun orderStatus(@PathVariable currencyPair: String, @PathVariable orderId: String): ResponseEntity<List<OrderStatusResponse>> {
        return ResponseEntity.ok(exchangeService.orderStatus(currencyPair, orderId))
    }

    @DeleteMapping("/orders/order")
    fun cancelOrder(@RequestBody request: CancelRequest): ResponseEntity<Void> {
        exchangeService.cancelOrder(request.orderId, request.pair)
        return ResponseEntity.ok().build()
    }

    data class CancelRequest(
        val orderId: String,
        val pair: String
    )
}