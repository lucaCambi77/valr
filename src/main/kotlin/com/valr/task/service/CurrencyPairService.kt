package com.valr.task.service

import com.valr.task.domain.CurrencyPair
import org.springframework.stereotype.Service

@Service
class CurrencyPairService {
    private val currencyPairs: MutableMap<String, CurrencyPair> = mutableMapOf(
        "BTCUSDC" to
                CurrencyPair(
                    symbol = "BTCUSDC",
                    baseCurrency = "BTC",
                    quoteCurrency = "USDC",
                    shortName = "BTC/USDC",
                    baseDecimalPlaces = 2
                )
    )

    fun getAllPairs(): MutableMap<String, CurrencyPair> {
        return currencyPairs
    }
}
