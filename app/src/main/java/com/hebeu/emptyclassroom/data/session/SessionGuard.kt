package com.hebeu.emptyclassroom.data.session

import com.hebeu.emptyclassroom.domain.error.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** 所有查询共享一次重登；每个查询最多重试一次。 */
class SessionGuard(private val holder: SessionStateHolder) {
    var recover: suspend () -> Boolean = { false }
    private val mutex = Mutex()
    private val revision = AtomicInteger()
    private val accountEpoch = AtomicInteger()
    private var recoveryFailure: Throwable? = null

    fun invalidate() { accountEpoch.incrementAndGet(); revision.incrementAndGet() }

    suspend fun <T> run(block: suspend () -> T): T {
        val epoch = accountEpoch.get()
        val ticket = revision.get()
        fun ensureAccount() { if (epoch != accountEpoch.get()) throw CancellationException("Account changed") }
        try {
            return block().also { ensureAccount() }
        } catch (e: AppError.SessionExpired) {
            ensureAccount()
            mutex.withLock {
                ensureAccount()
                if (ticket == revision.get()) {
                    recoveryFailure = null
                    try {
                        if (!recover()) {
                            holder.onExpired("登录已过期，请重新登录")
                            throw e
                        }
                    } catch (failure: Throwable) {
                        recoveryFailure = failure
                        throw failure
                    } finally {
                        revision.incrementAndGet()
                    }
                } else recoveryFailure?.let { throw it }
            }
            ensureAccount()
            try { return block().also { ensureAccount() } }
            catch (again: AppError.SessionExpired) {
                holder.onExpired("登录已过期，请重新登录")
                throw again
            }
        }
    }
}
