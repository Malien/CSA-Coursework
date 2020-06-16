package ua.edu.ukma.csa.network

import java.util.concurrent.atomic.AtomicInteger

inline class UserID(val id: UInt) {
    companion object {
        val SERVER = UserID(0u)

        private val userCount = AtomicInteger(0)
        fun assign() = UserID(userCount.incrementAndGet().toUInt())
    }
}
