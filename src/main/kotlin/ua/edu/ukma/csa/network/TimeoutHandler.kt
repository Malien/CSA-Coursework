package ua.edu.ukma.csa.network

import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlin.concurrent.thread

/**
 * Class that periodically checks to timeouts
 * @param E the type of entities used for invalidation
 * @constructor
 * @param sleepDuration time periods when timeout check should be run. Default is [Duration.ZERO]
 * if duration is [zero][Duration.ZERO], the checker will use [Thread.yield] instead of [Thread.sleep] between checks
 */
class TimeoutHandler<E>(private val sleepDuration: Duration = Duration.ZERO) : Closeable {

    /**
     * Class used to store timeout entities with their respective time to live. And timeout handler.
     * @param entity unique entity that is used as a key.
     * @param ttl an instant when entity should be invalidated.
     * @param handler function that is run to invalidate entity when it's expired.
     */
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

    /**
     * Thread that runs repeatedly and checks whether entity hit its `ttl`.
     */
    private val executionThread = thread(name = "Timeout-Handler") {
        while (!shouldStop) {
            if (sleepDuration == Duration.ZERO) Thread.yield()
            else Thread.sleep(sleepDuration.toMillis(), sleepDuration.nano)
            while (!queue.isEmpty() && queue.peek().ttl < Instant.now()) {
                val timeout = queue.poll()
                val (entity, _, handler) = timeout
                if (validityMap[entity] === timeout) {
                    handler(entity)
                    validityMap.remove(entity)
                }
            }
        }
    }

    /**
     * Enqueue entity to be timed out after a specified duration. If the same entity is enqueued again, it's ttl will
     * be reset to the latest one.
     * @param entity unique entity to be invalidated. Used internally as a key.
     * @param after a duration after current instant, when entity should be invalidated
     * @param handler function that is called when entity is expired. Use it to invalidate entity in your model.
     */
    fun timeout(entity: E, after: Duration, handler: (E) -> Unit) {
        val timeout = TimeoutEntity(entity, ttl = Instant.now() + after, handler = handler)
        queue.add(timeout)
        validityMap[entity] = timeout
    }

    /**
     * Stops the timeout checking routine. Joins the checker thread into current
     */
    override fun close() {
        shouldStop = true
        executionThread.join()
    }
}
