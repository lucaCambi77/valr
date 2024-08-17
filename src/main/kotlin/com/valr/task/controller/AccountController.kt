package com.valr.task.controller

import com.valr.task.domain.User
import com.valr.task.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.*

@RestController
@RequestMapping("/account")
class AccountController(private val userService: UserService, private val passwordEncoder: PasswordEncoder) {

    @PostMapping("/create")
    fun createUser(@RequestBody userRequest: UserRequest): ResponseEntity<String> {
        val userId = UUID.randomUUID().toString()

        val user = User(
            id = userId,
            name = userRequest.name,
            email = userRequest.email,
            password = passwordEncoder.encode(userRequest.password)
        )

        userService.update(user)
        return ResponseEntity(userId, HttpStatus.CREATED)
    }

    data class UserRequest(
        val name: String,
        val email: String,
        val password: String
    )

    @PatchMapping("/wallet")
    fun updateWallet(
        @RequestBody walletUpdate: WalletRequest,
        authentication: Authentication
    ): ResponseEntity<User> {
        val username = authentication.name
        val user = userService.get(username)

        user.wallet.baseBalances.putAll(walletUpdate.baseBalances)
        user.wallet.quoteBalances.putAll(walletUpdate.quoteBalances)
        userService.update(user)
        return ResponseEntity(user, HttpStatus.OK)
    }

    data class WalletRequest(
        val baseBalances: Map<String, BigDecimal> = emptyMap(),
        val quoteBalances: Map<String, BigDecimal> = emptyMap()
    )
}