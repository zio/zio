package zio

import zio.Supervisor.Propagation
import zio.ThreadLocalBridge.TrackingFiberRef
import zio.internal.FiberContext

trait ThreadLocalBridge[A] {
  def withFiberRef[R, E, A1](f: TrackingFiberRef[A] => ZIO[R, E, A1]): ZIO[R, E, A1]
}

object ThreadLocalBridge {
  def apply[A](initialValue: A)(link: A => Unit) =
    new ThreadLocalBridge[A] {
      override def withFiberRef[R, E, A1](f: TrackingFiberRef[A] => ZIO[R, E, A1]): ZIO[R, E, A1] =
        for {
          fiberRef  <- FiberRef.make(initialValue)
          _          = link(initialValue)
          supervisor = new FiberRefTrackingSupervisor
          _          = supervisor.trackFiberRef(fiberRef, link)
          res <- f(new TrackingFiberRef(fiberRef, link))
                   .supervised(supervisor)
        } yield res
    }

  private class FiberRefTrackingSupervisor extends Supervisor[Unit] {

    private val trackedRefs: Ref[Set[(FiberRef[_], Any => Unit)]] = Ref.unsafeMake(Set.empty)

    override def value: UIO[Unit] = ZIO.unit

    override def unsafeOnEnd[R, E, A1](value: Exit[E, A1], fiber: Fiber.Runtime[E, A1]) = Propagation.Continue

    override private[zio] def unsafeOnStart[R, E, A](
      environment: R,
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    ) = Propagation.Continue

    def trackFiberRef[B](fiberRef: FiberRef[B], link: B => Unit): Unit =
      trackedRefs.unsafeUpdate(old => old + ((fiberRef, link.asInstanceOf[Any => Unit])))

    override def unsafeOnSuspend[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      foreachTrackedRef { (fiberRef, link) =>
        link(fiberRef.initial)
      }

    override def unsafeOnResume[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      foreachTrackedRef { (fiberRef, link) =>
        val value = fiber.asInstanceOf[FiberContext[E, A1]].fiberRefLocals.get(fiberRef)
        if (value == null) {
          link(fiberRef.initial)
        } else {
          link(value)
        }
      }

    private def foreachTrackedRef(f: (FiberRef[_], Any => Unit) => Unit): Unit =
      trackedRefs.unsafeGet.foreach { case (fiberRef, link) =>
        f(fiberRef, link)
      }
  }

  final class TrackingFiberRef[A] private[zio] (fiberRef: FiberRef[A], link: A => Unit) {

    def set(value: A): IO[Nothing, Unit] =
      fiberRef.set(value) <* linkM(value)

    def update(f: A => A): UIO[Unit] = modify { v =>
      val result = f(v)
      ((), result)
    }

    def modify[B](f: A => (B, A)): UIO[B] =
      fiberRef.modify(f) <* (fiberRef.get >>= linkM)

    val get: UIO[A] = fiberRef.get

    def getAndSet(a: A): UIO[A] =
      fiberRef.getAndSet(a) <* linkM(a)

    def getAndUpdate(f: A => A): UIO[A] = modify { v =>
      val result = f(v)
      (v, result)
    }

    def getAndUpdateSome(pf: PartialFunction[A, A]): UIO[A] = modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (v, result)
    }

    def locally[R, E, B](value: A)(use: ZIO[R, E, B]): ZIO[R, E, B] =
      for {
        oldValue <- get
        b        <- set(value).bracket_(set(oldValue))(use)
      } yield b

    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): UIO[B] = modify { v =>
      pf.applyOrElse[A, (B, A)](v, _ => (default, v))
    }

    def updateAndGet(f: A => A): UIO[A] = modify { v =>
      val result = f(v)
      (result, result)
    }

    def updateLocally[R, E, B](f: A => A)(use: ZIO[R, E, B]): ZIO[R, E, B] =
      for {
        oldValue <- get
        b        <- set(f(oldValue)).bracket_(set(oldValue))(use)
      } yield b

    def updateSomeLocally[R, E, B](pf: PartialFunction[A, A])(use: ZIO[R, E, B]): ZIO[R, E, B] =
      for {
        oldValue <- get
        value     = pf.applyOrElse[A, A](oldValue, identity)
        b        <- set(value).bracket_(set(oldValue))(use)
      } yield b

    def updateSome(pf: PartialFunction[A, A]): UIO[Unit] = modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      ((), result)
    }

    def updateSomeAndGet(pf: PartialFunction[A, A]): UIO[A] = modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (result, result)
    }

    private def linkM(a: A) = UIO(link(a))
  }

}
