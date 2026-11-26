package zio

import zio.internal.FiberRuntime
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference

/**
 * A service that keeps a `ThreadLocal` (or other per-thread state) in sync with
 * a [[FiberRef]], for libraries that read the `ThreadLocal`, such as logging
 * MDC contexts. Each `FiberRef` it creates calls a `link` function with the
 * value that the thread running the fiber should see, and the function copies
 * it into the `ThreadLocal`.
 *
 * {{{
 * val requestId: ThreadLocal[String] = new ThreadLocal[String] {
 *   override def initialValue(): String = "none"
 * }
 *
 * val program: UIO[String] =
 *   ZIO.scoped {
 *     for {
 *       ref  <- ThreadLocalBridge.makeFiberRef("none")(id => requestId.set(id))
 *       _    <- ref.set("req-7")
 *       seen <- ZIO.succeed(requestId.get())
 *     } yield seen
 *   }.provide(ThreadLocalBridge.live)
 * // succeeds with req-7
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/state-management/threadlocal-bridge/ ThreadLocalBridge]]
 */
sealed trait ThreadLocalBridge {

  /**
   * Creates a `FiberRef` with the specified initial value whose value is
   * passed to `link`:
   *
   *   - the initial value when the `FiberRef` is created;
   *   - the current value after `set`, `update`, `modify` and `get`, which all
   *     go through `modify`;
   *   - the new value when `locally` starts, and the previous value when it
   *     ends;
   *   - the initial value when a fiber suspends, and that fiber's value when
   *     it resumes on a thread;
   *   - the initial value when the scope closes.
   *
   * `link` runs on the thread that runs the fiber, so it can set a
   * `ThreadLocal`.
   */
  def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope, Nothing, FiberRef[A]]
}

/**
 * Accessors and the layer of the [[ThreadLocalBridge]] service.
 */
object ThreadLocalBridge {
  private implicit val trace: Trace = Trace.empty

  /**
   * Creates a `FiberRef` synchronized with `link` through the
   * `ThreadLocalBridge` service of the environment. See
   * `ThreadLocalBridge#makeFiberRef`.
   */
  def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope with ThreadLocalBridge, Nothing, FiberRef[A]] =
    ZIO.serviceWithZIO[ThreadLocalBridge](_.makeFiberRef(initialValue)(link))

  /**
   * The `ThreadLocalBridge` service. Building the layer also installs a
   * supervisor that calls the `link` functions when fibers suspend and resume.
   */
  val live: ZLayer[Any, Nothing, ThreadLocalBridge] = ZLayer.suspend {
    val supervisor      = new FiberRefTrackingSupervisor
    val supervisorLayer = Runtime.addSupervisor(supervisor)
    val bridgeLayer = ZLayer.succeed {
      new ThreadLocalBridge {
        def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope, Nothing, FiberRef[A]] =
          for {
            fiberRef <- FiberRef.make(initialValue)
            _         = link(initialValue)
            _         = supervisor.trackFiberRef(fiberRef, link)(Unsafe.unsafe)
            _ <- Scope.addFinalizer(
                   ZIO.succeed {
                     link(initialValue)
                     supervisor.forgetFiberRef(fiberRef, link)(Unsafe.unsafe)
                   }
                 )
          } yield {
            new TrackingFiberRef(fiberRef, link)
          }
      }
    }
    supervisorLayer <*> bridgeLayer
  }

  private class FiberRefTrackingSupervisor extends Supervisor[Unit] {

    private val trackedRefs: Ref.Atomic[Set[(FiberRef[_], Any => Unit)]] =
      Ref.unsafe.make(Set.empty[(FiberRef[_], Any => Unit)])(Unsafe)

    override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

    override def onEnd[R, E, A1](value: Exit[E, A1], fiber: Fiber.Runtime[E, A1])(implicit unsafe: Unsafe): Unit =
      ()

    override def onStart[R, E, A1](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A1],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A1]
    )(implicit unsafe: Unsafe): Unit = ()

    def trackFiberRef[B](fiberRef: FiberRef[B], link: B => Unit)(implicit unsafe: Unsafe): Unit =
      trackedRefs.unsafe.update(old => old + ((fiberRef, link.asInstanceOf[Any => Unit])))

    def forgetFiberRef[B](fiberRef: FiberRef[B], link: B => Unit)(implicit unsafe: Unsafe): Unit =
      trackedRefs.unsafe.update(old => old - ((fiberRef, link.asInstanceOf[Any => Unit])))

    override def onSuspend[E, A1](fiber: Fiber.Runtime[E, A1])(implicit unsafe: Unsafe): Unit =
      foreachTrackedRef { (fiberRef, link) =>
        link(fiberRef.initial)
      }

    override def onResume[E, A1](fiber: Fiber.Runtime[E, A1])(implicit unsafe: Unsafe): Unit =
      foreachTrackedRef { (fiberRef, link) =>
        val value = fiber.asInstanceOf[FiberRuntime[E, A1]].getFiberRef(fiberRef)
        link(value)
      }

    private def foreachTrackedRef(f: (FiberRef[_], Any => Unit) => Unit)(implicit unsafe: Unsafe): Unit =
      trackedRefs.unsafe.get.foreach { case (fiberRef, link) =>
        f(fiberRef, link)
      }
  }

  /**
   * The `FiberRef` that `ThreadLocalBridge.live` creates: it delegates to
   * `fiberRef`, and passes the value to `link` after each `modify` (and so
   * after `get`, `set` and `update`), and when `locally` starts and ends.
   */
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
