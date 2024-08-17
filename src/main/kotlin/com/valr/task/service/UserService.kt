package com.valr.task.service

import com.valr.task.domain.User
import org.springframework.stereotype.Service

@Service
class UserService(
    private val users: MutableMap<String, User> = mutableMapOf()
) {
    fun get(userId: String): User {
        return users[userId] ?: throw Exception("User $userId not found")
    }

    fun update(user: User) {
        users[user.id] = user
    }
}