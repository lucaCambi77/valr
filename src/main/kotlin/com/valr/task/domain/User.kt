package com.valr.task.domain

import java.math.BigDecimal

data class User(
    val id: String,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val wallet: Wallet = Wallet()
)

data class Wallet(
    val baseBalances: MutableMap<String, BigDecimal> = mutableMapOf(),
    val quoteBalances: MutableMap<String, BigDecimal> = mutableMapOf(),
    val blockedBaseBalances: MutableMap<String, BigDecimal> = mutableMapOf(),
    val blockedQuoteBalances: MutableMap<String, BigDecimal> = mutableMapOf()
) {
    // Method to update base balances
    fun updateBaseBalance(currency: String, amount: BigDecimal) {
        baseBalances[currency] = (baseBalances[currency] ?: BigDecimal.ZERO) + amount
    }

    // Method to update quote balances
    fun updateQuoteBalance(currency: String, amount: BigDecimal) {
        quoteBalances[currency] = (quoteBalances[currency] ?: BigDecimal.ZERO) + amount
    }

    // Method to update blocked base balances
    fun updateBlockedBaseBalance(currency: String, amount: BigDecimal) {
        blockedBaseBalances[currency] = (blockedBaseBalances[currency] ?: BigDecimal.ZERO) + amount
    }

    // Method to update blocked quote balances
    fun updateBlockedQuoteBalance(currency: String, amount: BigDecimal) {
        blockedQuoteBalances[currency] = (blockedQuoteBalances[currency] ?: BigDecimal.ZERO) + amount
    }
}