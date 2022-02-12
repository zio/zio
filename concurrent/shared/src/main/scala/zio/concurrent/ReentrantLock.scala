package zio.concurrent

import zio._
import scala.util.Random

final class ReentrantLock private (fairness: Boolean, state: Ref[ReentrantLock.State]) {
  import ReentrantLock.State

  def hasQueuedFiber(fiberId: Fiber.Id): UIO[Boolean] =
    state.get.map(_.waiters.contains(fiberId))

  lazy val hasQueuedFibers: UIO[Boolean] =
    state.get.map(_.waiters.nonEmpty)

  def isFair: Boolean = fairness

  lazy val isHeldByCurrentFiber: UIO[Boolean] =
    ZIO.fiberId.flatMap { fiberId =>
      state.get.map {
        case State(_, Some(`fiberId`), _, _) => true
        case _                               => false
      }
    }

  lazy val locked: UIO[Boolean] =
    state.get.map(_.holder.nonEmpty)

  lazy val holdCount: UIO[Int] =
    ZIO.fiberId.flatMap { fiberId =>
      state.get.map {
        case State(_, Some(`fiberId`), cnt, _) => cnt
        case _                                 => 0
      }
    }

  lazy val queueLength: UIO[Int] =
    state.get.map(_.waiters.size)

  lazy val queuedFibers: UIO[List[Fiber.Id]] =
    state.get.map(_.waiters.keys.toList)

  lazy val lock: UIO[Unit] =
    (ZIO.fiberId <*> Promise.make[Nothing, Unit]).flatMap { case (fiberId, p) =>
      state.modify {
        case State(ep, None, _, _) =>
          UIO.unit -> State(ep + 1, Some(fiberId), 1, Map.empty)
        case State(ep, Some(`fiberId`), cnt, waiters) =>
          UIO.unit -> State(ep + 1, Some(fiberId), cnt + 1, waiters)
        case State(ep, holder, cnt, waiters) =>
          p.await.unit -> State(ep + 1, holder, cnt, waiters + (fiberId -> (ep -> p)))
      }.flatten
    }

  lazy val owner: UIO[Option[Fiber.Id]] =
    state.get.map(_.holder)

  lazy val tryLock: UIO[Boolean] =
    ZIO.fiberId.flatMap { fiberId =>
      state.modify {
        case State(ep, None, _, _) =>
          true -> State(ep + 1, Some(fiberId), 1, Map.empty)
        case otherwise =>
          false -> otherwise
      }
    }

  lazy val unlock: UIO[Unit] =
    ZIO.fiberId.flatMap { fiberId =>
      state.modify {
        case State(ep, Some(`fiberId`), 1, holders) =>
          relock(ep, holders)
        case State(ep, Some(`fiberId`), cnt, holders) =>
          UIO.unit -> State(ep, Some(fiberId), cnt - 1, holders)
        case otherwise =>
          UIO.unit -> otherwise
      }.flatten
    }

  lazy val withLock: UManaged[Int] =
    ZManaged.make(lock *> holdCount)(_ => unlock)

  private def relock(epoch: Long, holders: Map[Fiber.Id, (Long, Promise[Nothing, Unit])]): (UIO[Unit], State) = {
    val nextHolder = if (fairness) holders.minByOption(_._2._1) else pickRandom(holders)
    nextHolder match {
      case Some((fiberId, (_, promise))) =>
        promise.succeed(()).unit -> State(epoch + 1, Some(fiberId), 1, holders - fiberId)
      case None =>
        UIO.unit ->
          State(epoch + 1, None, 0, Map.empty)
    }
  }

  private def pickRandom(
    holders: Map[Fiber.Id, (Long, Promise[Nothing, Unit])]
  ): Option[(Fiber.Id, (Long, Promise[Nothing, Unit]))] =
    if (holders.isEmpty)
      None
    else {
      val n  = Random.between(0L, holders.size.toLong)
      val it = holders.iterator
      var i  = 0

      var result: (Fiber.Id, (Long, Promise[Nothing, Unit])) = null
      while (it.hasNext && i < n) {
        it.next()
        i += 1
      }
      if (i == n) result = it.next()

      Option(result)
    }

}

object ReentrantLock {
  def make(fairness: Boolean): UIO[ReentrantLock] =
    ZRef.make(State.empty).map(new ReentrantLock(fairness, _))

  private case class State(
    epoch: Long,
    holder: Option[Fiber.Id],
    holdCount: Int,
    waiters: Map[Fiber.Id, (Long, Promise[Nothing, Unit])]
  )

  private object State {
    val empty: State = State(0L, None, 0, Map.empty)
  }
}
