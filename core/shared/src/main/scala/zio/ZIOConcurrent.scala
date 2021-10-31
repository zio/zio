package zio

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import zio.Synchronizer._

final class CountDownLatch(count: Int, maxWaiters: Option[Int]) {
  private[this] val sync = Synchronizer(count, maxWaiters)

  // TODO is it possible to evaluate once and reuse the Promise?
  private[this] val awaitZero = sync.acquire(count)

  def await: UIO[Unit] = awaitZero.asInstanceOf[UIO[Unit]]

  // TODO stop at zero
  def countDown(): UIO[Unit] = sync.release(1)
}

final class AsyncSemaphore(initialPermits: Int, maxWaiters: Option[Int]) {
  private[this] val sync = Synchronizer(initialPermits, maxWaiters)

  def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Either[E, Full], A] =
    sync
      .acquire(1)
      .mapError(Right(_))
      .flatMap { _ =>
        zio
          .mapError(Left(_))
          .ensuring(sync.release(1))
      }
}

final class ReentrantLock(maxWaiters: Option[Int]) {
  @volatile private[this] var owner: Fiber.Id = null

  private[this] val sem = new AsyncSemaphore(1, maxWaiters)

  def apply[R, E <: Throwable, A](zio: ZIO[R, E, A]): ZIO[R, Either[E, Full], A] =
    ZIO.effectSuspendTotalWith { (_, fiberId) =>
      if (owner == fiberId) {
        zio.mapError(Left(_))
      } else {
        sem(ZIO.effectSuspendTotal {
          owner = fiberId
          zio.ensuring(ZIO.effectTotal(owner = null))
        })
      }
    }
}

final class Synchronizer(initialPermits: Int, maxWaiters: Int) {

  // if >= 0, # of permits
  // if < 0, # of waiters
  private[this] val state = new AtomicInteger(initialPermits)

  // TODO requires multiple allocations per waiter
  private[this] val waiters = new ConcurrentLinkedQueue[(AtomicInteger, Promise[Full, Unit])]

  def acquire(permits: Int): IO[Full, Unit] =
    IO.effectSuspendTotalWith { (_, fiberId) =>
      @tailrec def loop(): IO[Full, Unit] = {
        val s = state.get()
        if (s == -maxWaiters) {
          // full
          Synchronizer.full
        } else if (s >= permits) {
          // enough available permits
          if (state.compareAndSet(s, s - permits))
            Task.unit
          else
            loop()
        } else {
          // not enough permits, add waiter
          val ns = if (s >= 0) -1 else s - 1
          if (state.compareAndSet(s, ns)) {
            val p   = Promise.unsafeMake[Full, Unit](fiberId)
            val rem = if (s > 0) permits - s else permits
            waiters.add((new AtomicInteger(rem), p))
            p.await
          } else {
            loop()
          }
        }
      }
      loop()
    }

  def release(permits: Int): UIO[Unit] =
    UIO.effectSuspendTotal {
      @tailrec def loop(permits: Int): UIO[Unit] =
        if (permits == 0) {
          UIO.unit
        } else {
          val s = state.get()
          if (s >= 0) {
            if (state.compareAndSet(s, s + permits)) {
              UIO.unit
            } else {
              loop(permits)
            }
          } else {
            val w = waiters.peek()
            if (w != null) {
              val (waiterPermits, promise) = w
              val wp                       = waiterPermits.get()
              if (wp == 0) {
                // another release call is polling the waiter
                loop(permits)
              } else if (wp <= permits) {
                // enough permits to release the waiter
                if (waiterPermits.compareAndSet(wp, 0)) {
                  // at this point, concurrent release calls need to wait
                  // for the waiter to be polled
                  // TODO these should never fail but if it does what's the best way to fail because of a bug?
                  require(waiters.poll() == w)
                  require(state.incrementAndGet() <= 0)
                  promise
                    .succeed(())
                    .join(release(permits - wp))
                    .unit
                    // TODO how do I drop the environment?
                    .asInstanceOf[UIO[Unit]]
                } else {
                  loop(permits)
                }
              } else {
                // not enough permits to release the waiter
                // so just update the number of permits the waiter
                // is waiting for
                if (waiterPermits.compareAndSet(wp, wp - permits)) {
                  UIO.unit
                } else {
                  loop(permits)
                }
              }
            } else {
              loop(permits)
            }
          }
        }
      loop(permits)
    }
}

object Synchronizer {
  case class Full() extends Exception
  private val full = IO.fail(Full())

  def apply(permits: Int, maxWaiters: Option[Int]) =
    new Synchronizer(permits, maxWaiters.getOrElse(Int.MaxValue))
}
