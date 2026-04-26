package zio.internal.concurrent

import zio.{Fiber, FiberId, Scope, UIO, Unsafe, ZIO, ZScheduler}
import zio.internal.{OneShot, WeakConcurrentBag}

import java.lang.ref.{PhantomReference, ReferenceQueue}
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec

/**
 * A high-performance, thread-safe, weakly referenced set of fibers.
 *
 * This structure allows concurrent addition and removal of fibers, with eventual consistency
 * during iteration. It uses weak references to avoid memory leaks from long-lived references
 * to fibers that have already completed or been interrupted.
 *
 * The implementation leverages a reference queue to efficiently clean up stale entries without
 * creating a weak reference per fiber. Instead, it uses a token-based system where each fiber
 * is associated with a `Token` that is enqueued upon garbage collection.
 *
 * This structure is Loom-friendly and avoids synchronization bottlenecks.
 */
private[zio] final class FiberSet private (
  map: ConcurrentHashMap[Token, Entry],
  queue: ReferenceQueue[Any],
  cleaner: Fiber[Throwable, Nothing]
) {

  /**
   * Adds a fiber to the set.
   *
   * @return a token that can be used to remove the fiber
   */
  def add(fiber: Fiber[Any, Any]): Token = {
    val token = new Token(fiber.id)
    val entry = new Entry(fiber, token, queue)
    map.put(token, entry)
    token
  }

  /**
   * Removes a fiber from the set using the token returned by `add`.
   */
  def remove(token: Token): Unit = {
    map.remove(token)
  }

  /**
   * Applies the specified function to each fiber in the set.
   *
   * Note: due to the use of weak references, some fibers may have already been
   * finalized and thus will not be visited by this method.
   */
  def foreach(f: Fiber[Any, Any] => Unit): Unit = {
    val iter = map.values().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val fiber = entry.fiber.get()
      if (fiber ne null) f(fiber)
    }
  }

  /**
   * Returns the current number of active fibers in the set.
   *
   * Note: this is an eventually consistent estimate and may include entries
   * for fibers that have recently been garbage collected but not yet cleaned.
   */
  def size(): Int = {
    @tailrec
    def loop(acc: Int, count: Int): Int =
      if (count > 10) acc // prevent infinite loop in degenerate cases
      else {
        val iter = map.values().iterator()
        var size = 0
        var cleaned = false
        while (iter.hasNext) {
          val entry = iter.next()
          if (entry.fiber.get() eq null) {
            map.remove(entry.token)
            cleaned = true
          } else {
            size += 1
          }
        }
        if (cleaned) loop(size, count + 1)
        else size
      }

    loop(0, 0)
  }

  /**
   * Shuts down this fiber set, stopping the cleaner fiber.
   */
  def shutdown: UIO[Unit] =
    ZIO.fiberIdWith { fiberId =>
      Unsafe.unsafe { implicit u =>
        if (fiberId != FiberId.None) cleaner.interruptAs(fiberId)
        else cleaner.interrupt
      }
    }
}

private[zio] object FiberSet {

  /**
   * Creates a new fiber set.
   */
  def make: UIO[FiberSet] = {
    val map = new ConcurrentHashMap[Token, Entry]()
    val queue = new ReferenceQueue[Any]()
    val set = new FiberSet(map, queue, ZScheduler.currentThreadScheduler.fork(cleanUpLoop(map, queue)))
    ZIO.succeed(set)
  }

  @tailrec
  private def cleanUpLoop(
    map: ConcurrentHashMap[Token, Entry],
    queue: ReferenceQueue[Any]
  ): UIO[Nothing] =
    ZIO
      .attempt {
        val ref = queue.remove(100L)
        if (ref ne null) {
          val token = ref.asInstanceOf[PhantomReferenceWithToken].token
          map.remove(token)
        }
      }
      .orDie
      .flatMap(_ => cleanUpLoop(map, queue))
}

/**
 * A token that uniquely identifies a fiber in the set.
 *
 * This is used to remove fibers from the set without holding a strong reference to the fiber.
 */
private[zio] final class Token(val fiberId: FiberId) {
  override def equals(that: Any): Boolean =
    that match {
      case that: Token => this.fiberId == that.fiberId
      case _           => false
    }

  override def hashCode(): Int =
    fiberId.hashCode()
}

/**
 * An entry in the fiber set.
 *
 * Holds a weak reference to the fiber and the token used to identify it.
 */
private[zio] final class Entry(
  val fiber: PhantomReferenceWithToken,
  val token: Token,
  queue: ReferenceQueue[Any]
) {
  def this(fiber: Fiber[Any, Any], token: Token, queue: ReferenceQueue[Any]) =
    this(new PhantomReferenceWithToken(fiber, queue, token), token, queue)
}

/**
 * A phantom reference that carries a token.
 *
 * This allows us to identify which entry to remove when the fiber is garbage collected.
 */
private[zio] final class PhantomReferenceWithToken(
  referent: Any,
  queue: ReferenceQueue[Any],
  val token: Token
) extends PhantomReference[Any](referent, queue)