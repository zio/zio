package zio

import zio.internal.FiberContext
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference

trait ThreadLocalBridge {
  def makeFiberRef[A](initialValue: A, link: A => Unit): ZIO[Scope, Nothing, FiberRef[A]]
}

object ThreadLocalBridge {
  private implicit val trace: Trace = Trace.empty

  def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope with ThreadLocalBridge, Nothing, FiberRef[A]] =
    ZIO.serviceWithZIO[ThreadLocalBridge](_.makeFiberRef(initialValue, link))

  val live: ZLayer[Any, Nothing, ThreadLocalBridge] = ZLayer.suspend {
    val supervisor      = new FiberRefTrackingSupervisor
    val supervisorLayer = Runtime.addSupervisor(supervisor)
    val bridgeLayer = ZLayer.succeed {
      new ThreadLocalBridge {
        def makeFiberRef[A](initialValue: A, link: A => Unit): ZIO[Scope, Nothing, FiberRef[A]] =
          for {
            fiberRef <- FiberRef.make(initialValue)
            _         = link(initialValue)
            _         = supervisor.trackFiberRef(fiberRef, link)
            _ <- Scope.addFinalizer(
                   ZIO.succeed {
                     link(initialValue)
                     supervisor.forgetFiberRef(fiberRef, link)
                   }
                 )
          } yield {
            new TrackingFiberRef(fiberRef, link)
          }
      }
    }
    supervisorLayer ++ bridgeLayer ++ Runtime.superviseOperations
  }

  private class FiberRefTrackingSupervisor extends Supervisor[Unit] {

    private val trackedRefs: Ref.Atomic[Set[(FiberRef[_], Any => Unit)]] = Ref.Atomic(new AtomicReference(Set.empty))

    override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

    override def unsafeOnEnd[R, E, A1](value: Exit[E, A1], fiber: Fiber.Runtime[E, A1]): Unit = ()

    override def unsafeOnStart[R, E, A1](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A1],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A1]
    ): Unit = ()

    def trackFiberRef[B](fiberRef: FiberRef[B], link: B => Unit): Unit =
      trackedRefs.unsafeUpdate(old => old + ((fiberRef, link.asInstanceOf[Any => Unit])))

    def forgetFiberRef[B](fiberRef: FiberRef[B], link: B => Unit): Unit =
      trackedRefs.unsafeUpdate(old => old - ((fiberRef, link.asInstanceOf[Any => Unit])))

    override def unsafeOnSuspend[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      foreachTrackedRef { (fiberRef, link) =>
        link(fiberRef.initial)
      }

    override def unsafeOnResume[E, A1](fiber: Fiber.Runtime[E, A1]): Unit =
      foreachTrackedRef { (fiberRef, link) =>
        val value = fiber.asInstanceOf[FiberContext[E, A1]].unsafeGetRef(fiberRef)
        link(value)
      }

    private def foreachTrackedRef(f: (FiberRef[_], Any => Unit) => Unit): Unit =
      trackedRefs.unsafeGet.foreach { case (fiberRef, link) =>
        f(fiberRef, link)
      }
  }

  class TrackingFiberRef[A](fiberRef: FiberRef[A], link: A => Unit) extends FiberRef.Proxy[A](fiberRef) {

    override def locally[R, EC >: Nothing, C](value: A)(use: ZIO[R, EC, C])(implicit trace: Trace): ZIO[R, EC, C] =
      fiberRef.get.flatMap { before =>
        fiberRef.locally(value) {
          (linkM(value) *> use)
            .ensuring(linkM(before))
        }
      }

    override def modify[B](f: A => (B, A))(implicit trace: Trace): UIO[B] =
      for {
        b    <- fiberRef.modify(f)
        newA <- fiberRef.get
        _    <- linkM(newA)
      } yield b

    private def linkM(a: A) =
      ZIO.succeed(link(a))
  }

}
