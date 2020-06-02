package ua.edu.ukma.csa.network.udp

import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlin.concurrent.thread

class TimeoutHandler<E>(private val sleepDuration: Duration = Duration.ZERO) : Closeable {
    inner class TimeoutEntity(val entity: E, val ttl: Instant, val handler: (E) -> Unit) : Comparable<TimeoutEntity> {
        operator fun component1() = entity
        operator fun component2() = ttl
        operator fun component3() = handler
        override fun toString() = "TimeoutEntity(entity=$entity, ttl=$ttl)"
        override fun compareTo(other: TimeoutEntity) = ttl.compareTo(other.ttl)
    }

    private val queue = PriorityBlockingQueue<TimeoutEntity>()
    private val validityMap = ConcurrentHashMap<E, TimeoutEntity>(100)

    @Volatile
    var shouldStop = false

    private val executionThread = thread {
        while (!shouldStop) {
            if (queue.isEmpty()) Thread.yield()
            val now = Instant.now()
            while (queue.peek().ttl < now) {
                val timeout = queue.poll()
                val (entity, _, handler) = timeout
                if (validityMap[entity] === timeout) {
                    handler(entity)
                    validityMap.remove(entity)
                }
            }
            if (sleepDuration == Duration.ZERO) Thread.yield()
            else Thread.sleep(sleepDuration.toMillis(), sleepDuration.nano)
        }
    }

    fun timeout(entity: E, after: Duration, handler: (E) -> Unit) {
        val timeout = TimeoutEntity(entity, ttl = Instant.now() + after, handler = handler)
        queue.add(timeout)
        validityMap[entity] = timeout
    }

    override fun close() {
        shouldStop = true
        executionThread.join()
    }
}
