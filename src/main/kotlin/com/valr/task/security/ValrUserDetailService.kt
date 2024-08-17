package com.valr.task.security

import com.valr.task.domain.User
import com.valr.task.service.UserService
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userService: UserService
) : UserDetailsService {

    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userService.get(username)
        return CustomUserDetails(user)
    }
}

class CustomUserDetails(private val user: User) : UserDetails {

    override fun getAuthorities() = emptyList<GrantedAuthority>()

    override fun isEnabled() = true

    override fun isCredentialsNonExpired() = true

    override fun isAccountNonExpired() = true

    override fun isAccountNonLocked() = true

    override fun getPassword() = user.password

    override fun getUsername() = user.id
}