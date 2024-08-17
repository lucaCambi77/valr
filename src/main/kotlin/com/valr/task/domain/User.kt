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
    val quoteBalances: MutableMap<String, BigDecimal> = mutableMapOf()
)