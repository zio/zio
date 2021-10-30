package zio.concurrent

import zio._

final class CyclicBarrier(
  private val _parties: Int,
  private val _waiting: Ref[Int],
  private val _lock: Ref[Promise[Unit, Unit]],
  private val _action: UIO[Any],
  private val _broken: Ref[Boolean]
) {

  /** The number of parties required to trip this barrier. */
  def parties: Int = _parties

  /** The number of parties currently waiting at the barrier. */
  val waiting: UIO[Int] = _waiting.get

  /** Waits until all parties have invoked await on this barrier. Fails if the barrier is broken. */
  val await: IO[Unit, Int] =
    _broken.get.flatMap(if (_) IO.fail(()) else UIO.unit) *>
      _waiting.modify {
        case n if n + 1 == parties => _action *> succeed.as(_parties - n - 1)                            -> (n + 1)
        case n                     => _lock.get.flatMap(_.await.onInterrupt(break)).as(_parties - n - 1) -> (n + 1)
      }.flatten

  /** Resets the barrier to its initial state. Breaks any waiting party. */
  val reset: UIO[Unit] =
    for {
      w <- waiting
      _ <- if (w > 0)
             (fail *>
               Promise.make[Unit, Unit].flatMap(_lock.set) *>
               _waiting.set(0) *>
               _broken.set(false)).uninterruptible
           else UIO.unit
    } yield ()

  val isBroken: UIO[Boolean] = _broken.get

  private val break: UIO[Unit] =
    _broken.set(true) *> fail

  private val fail: UIO[Unit] =
    _lock.get.flatMap(_.fail(()).unit)

  private val succeed: UIO[Unit] =
    _lock.get.flatMap(_.succeed(()).unit)
}

object CyclicBarrier {
  def make(parties: Int): UIO[CyclicBarrier] =
    make(parties, UIO.unit)

  def make(parties: Int, action: UIO[Any]): UIO[CyclicBarrier] =
    for {
      waiting <- Ref.make(0)
      broken  <- Ref.make(false)
      lock    <- Promise.make[Unit, Unit].flatMap(Ref.make(_))
    } yield new CyclicBarrier(parties, waiting, lock, action, broken)
}
