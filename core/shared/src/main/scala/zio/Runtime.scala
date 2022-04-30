/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.internal.{FiberContext, FiberScope, Platform, StackBool, StackTraceBuilder}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.Future
import java.lang.ref.WeakReference

/**
 * A `Runtime[R]` is capable of executing tasks within an environment `R`.
 */
trait Runtime[+R] { self =>

  /**
   * The environment of the runtime.
   */
  def environment: ZEnvironment[R]

  /**
   * The `FiberRef` values that will be used for workflows executed by the
   * runtime.
   */
  def fiberRefs: FiberRefs =
    FiberRefs.empty

  /**
   * Constructs a new `Runtime` with the specified new environment.
   */
  def as[R1](r1: ZEnvironment[R1]): Runtime[R1] =
    map(_ => r1)

  def blockingExecutor: Executor =
    fiberRefs.getOrDefault(FiberRef.currentBlockingExecutor)

  def executor: Executor =
    fiberRefs.getOrDefault(FiberRef.currentExecutor)

  def flags: Set[RuntimeFlag] =
    fiberRefs.getOrDefault(FiberRef.currentRuntimeFlags)

  def isFatal(t: Throwable): Boolean =
    fiberRefs.getOrDefault(FiberRef.currentFatal).exists(_.isAssignableFrom(t.getClass))

  def loggers: Set[ZLogger[String, Any]] =
    fiberRefs.getOrDefault(FiberRef.currentLoggers)

  /**
   * Constructs a new `Runtime` by mapping the environment.
   */
  def map[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime[R1] =
    Runtime(f(environment), fiberRefs)

  final def reportFatal(t: Throwable): Nothing =
    fiberRefs.getOrDefault(FiberRef.currentReportFatal)(t)

  /**
   * Runs the effect "purely" through an async boundary. Useful for testing.
   */
  final def run[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): IO[E, A] =
    ZIO.fiberId.flatMap { fiberId =>
      ZIO.asyncInterrupt[Any, E, A] { callback =>
        val canceler = unsafeRunAsyncCancelable(zio)(exit => callback(ZIO.done(exit)))
        Left(ZIO.succeedBlocking(canceler(fiberId)))
      }
    }

  final def supervisors: Set[Supervisor[Any]] =
    fiberRefs.getOrDefault(FiberRef.currentSupervisors)

  /**
   * Executes the effect synchronously, failing with [[zio.FiberFailure]] if
   * there are any errors. May fail on Scala.js if the effect cannot be entirely
   * run synchronously.
   *
   * This method is effectful and should only be done at the edges of your
   * program.
   */
  final def unsafeRun[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): A =
    unsafeRunSync(zio).getOrElse(c => throw FiberFailure(c))

  /**
   * Executes the effect asynchronously, discarding the result of execution.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunAsync[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Unit =
    unsafeRunAsyncWith(zio)(_ => ())

  /**
   * Executes the effect asynchronously, eventually passing the exit value to
   * the specified callback. It returns a callback, which can be used to
   * interrupt the running execution.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunSync[E, A](zio0: ZIO[R, E, A])(implicit trace: Trace): Exit[E, A] =
    defaultUnsafeRunSync(zio0)

  private[zio] final def defaultUnsafeRunSync[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Exit[E, A] = {
    val result = internal.OneShot.make[Exit[E, A]]

    unsafeRunWith(zio)(result.set)

    result.get()
  }

  private[zio] def unsafeRunSyncFast[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Exit[E, A] =
    try {
      Exit.Success(unsafeRunFast(zio, 50))
    } catch {
      case failure: ZIO.ZioError[_, _] => failure.exit.asInstanceOf[Exit[E, A]]
    }

  private[zio] def unsafeRunFast[E, A](zio: ZIO[R, E, A], maxStack: Int)(implicit
    trace0: Trace
  ): A = {
    import ZIO.TracedCont
    import Runtime.{Lazy, UnsafeSuccess}

    type Erased  = ZIO[Any, Any, Any]
    type ErasedK = TracedCont[Any, Any, Any, Any]

    val nullK = null.asInstanceOf[ErasedK]

    def loop(zio: ZIO[R, _, _], stack: Int, stackTraceBuilder: Lazy[StackTraceBuilder]): UnsafeSuccess =
      if (stack >= maxStack) {
        defaultUnsafeRunSync(zio) match {
          case Exit.Success(success) => success.asInstanceOf[UnsafeSuccess]
          case Exit.Failure(cause)   => throw new ZIO.ZioError(Exit.failCause(cause), zio.trace)
        }
      } else {
        var curZio         = zio.asInstanceOf[Erased]
        var x1, x2, x3, x4 = nullK
        var success        = null.asInstanceOf[UnsafeSuccess]

        while (success eq null) {
          try {
            curZio.tag match {
              case ZIO.Tags.FlatMap =>
                val zio = curZio.asInstanceOf[ZIO.FlatMap[R, E, Any, A]]

                val k = zio.asInstanceOf[ErasedK]

                if (x4 eq null) {
                  x4 = x3; x3 = x2; x2 = x1; x1 = k

                  curZio = zio.zio.asInstanceOf[Erased]
                } else {
                  // Our "register"-based stack can't handle it, try consuming more JVM stack:
                  curZio = k(loop(zio.zio, stack + 1, stackTraceBuilder))
                }

              case ZIO.Tags.SucceedNow =>
                val zio = curZio.asInstanceOf[ZIO.SucceedNow[Any]]

                if (x1 ne null) {
                  val k = x1
                  x1 = x2; x2 = x3; x3 = x4; x4 = nullK
                  curZio = k(zio.value)
                } else {
                  success = zio.value.asInstanceOf[UnsafeSuccess]
                }

              case ZIO.Tags.Fail =>
                val zio = curZio.asInstanceOf[ZIO.Fail[E]]

                throw new ZIO.ZioError(Exit.failCause(zio.cause), zio.trace)

              case ZIO.Tags.Succeed =>
                val zio = curZio.asInstanceOf[ZIO.Succeed[Any]]

                if (x1 ne null) {
                  val k = x1
                  x1 = x2; x2 = x3; x3 = x4; x4 = nullK
                  curZio = k(zio.effect())
                } else {
                  success = zio.effect().asInstanceOf[UnsafeSuccess]
                }

              case _ =>
                val zio = curZio

                // Give up, the mini-interpreter can't handle it:
                defaultUnsafeRunSync(zio) match {
                  case Exit.Success(value) =>
                    if (x1 ne null) {
                      val k = x1
                      x1 = x2; x2 = x3; x3 = x4; x4 = nullK
                      curZio = k(value)
                    } else {
                      success = value.asInstanceOf[UnsafeSuccess]
                    }

                  case Exit.Failure(cause) => throw new ZIO.ZioError(Exit.failCause(cause), zio.trace)
                }
            }
          } catch {
            case failure: ZIO.ZioError[_, _] =>
              val builder = stackTraceBuilder.value

              builder += failure.trace
              if (x1 ne null) {
                builder += x1.trace
                if (x2 ne null) {
                  builder += x2.trace
                  if (x3 ne null) {
                    builder += x3.trace
                    if (x4 ne null) {
                      builder += x4.trace
                    }
                  }
                }
              }

              throw failure

            case t: Throwable =>
              val builder = stackTraceBuilder.value

              builder += zio.trace

              if (x1 ne null) {
                builder += x1.trace
                if (x2 ne null) {
                  builder += x2.trace
                  if (x3 ne null) {
                    builder += x3.trace
                    if (x4 ne null) {
                      builder += x4.trace
                    }
                  }
                }
              }

              if (!isFatal(t)) throw new ZIO.ZioError(Exit.die(t), trace0)
              else fiberRefs.getOrDefault(FiberRef.currentReportFatal)(t)
          }
        }

        success
      }

    val stackTraceBuilder = Lazy.stackTraceBuilder()

    try {
      loop(zio, 0, stackTraceBuilder).asInstanceOf[A]
    } catch {
      case failure: ZIO.ZioError[_, _] =>
        failure.exit match {
          case Exit.Success(value) =>
            throw new ZIO.ZioError(Exit.succeed(value), trace0)

          case Exit.Failure(cause) =>
            val fiberId = cause.trace.fiberId.getOrElse(FiberId.unsafeMake(trace0))

            val trace = StackTrace(fiberId, stackTraceBuilder.value.result())

            throw new ZIO.ZioError(Exit.failCause(cause.traced(trace)), trace0)
        }

    }
  }

  /**
   * Executes the effect asynchronously, eventually passing the exit value to
   * the specified callback.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunAsyncWith[E, A](
    zio: ZIO[R, E, A]
  )(k: Exit[E, A] => Any)(implicit trace: Trace): Unit = {
    unsafeRunAsyncCancelable(zio)(k)
    ()
  }

  /**
   * Executes the effect asynchronously, eventually passing the exit value to
   * the specified callback. It returns a callback, which can be used to
   * interrupt the running execution.
   *
   * This method is effectful and should only be invoked at the edges of your
   * program.
   */
  final def unsafeRunAsyncCancelable[E, A](
    zio: ZIO[R, E, A]
  )(k: Exit[E, A] => Any)(implicit trace: Trace): FiberId => Exit[E, A] = {
    lazy val curZio = zio
    val canceler    = unsafeRunWith(curZio)(k)
    fiberId => {
      val result = internal.OneShot.make[Exit[E, A]]
      canceler(fiberId)(result.set)
      result.get()
    }
  }

  /**
   * Executes the Task/RIO effect synchronously, failing with the original
   * `Throwable` on both [[Cause.Fail]] and [[Cause.Die]]. In addition, appends
   * a new element to the `Throwable`s "caused by" chain, with this `Cause`
   * "pretty printed" (in stackless mode) as the message. May fail on Scala.js
   * if the effect cannot be entirely run synchronously.
   *
   * This method is effectful and should only be done at the edges of your
   * program.
   */
  final def unsafeRunTask[A](task: RIO[R, A])(implicit trace: Trace): A =
    unsafeRunSync(task).fold(cause => throw cause.squashTrace, identity)

  /**
   * Runs the IO, returning a Future that will be completed when the effect has
   * been executed.
   *
   * This method is effectful and should only be used at the edges of your
   * program.
   */
  final def unsafeRunToFuture[E <: Throwable, A](
    zio: ZIO[R, E, A]
  )(implicit trace: Trace): CancelableFuture[A] = {
    val p: scala.concurrent.Promise[A] = scala.concurrent.Promise[A]()

    val canceler = unsafeRunWith(zio)(_.fold(cause => p.failure(cause.squashTraceWith(identity)), p.success))

    new CancelableFuture[A](p.future) {
      def cancel(): Future[Exit[Throwable, A]] = {
        val p: scala.concurrent.Promise[Exit[Throwable, A]] = scala.concurrent.Promise[Exit[Throwable, A]]()
        canceler(FiberId.None)(p.success)
        p.future
      }
    }
  }

  private final def unsafeRunWith[E, A](
    zio: ZIO[R, E, A]
  )(k: Exit[E, A] => Any)(implicit trace: Trace): FiberId => (Exit[E, A] => Any) => Unit = {
    val canceler = unsafeRunWithRefs(zio, fiberRefs)((exit, _) => k(exit))
    fiberId => k => canceler(fiberId)((exit, _) => k(exit))
  }

  private final def unsafeRunWithRefs[E, A](
    zio: ZIO[R, E, A],
    fiberRefs: FiberRefs
  )(
    k: (Exit[E, A], FiberRefs) => Any
  )(implicit trace: Trace): FiberId => ((Exit[E, A], FiberRefs) => Any) => Unit = {
    val fiberId = FiberId.unsafeMake(trace)

    val children = Platform.newWeakSet[FiberContext[_, _]]()

    val runtimeFiberRefs: Map[FiberRef[_], Any] =
      Map(
        FiberRef.currentBlockingExecutor -> fiberRefs.getOrDefault(FiberRef.currentBlockingExecutor),
        FiberRef.currentEnvironment      -> environment,
        FiberRef.currentExecutor         -> fiberRefs.getOrDefault(FiberRef.currentExecutor),
        FiberRef.currentFatal            -> fiberRefs.getOrDefault(FiberRef.currentFatal),
        FiberRef.currentLoggers          -> fiberRefs.getOrDefault(FiberRef.currentLoggers),
        FiberRef.currentReportFatal      -> fiberRefs.getOrDefault(FiberRef.currentReportFatal),
        FiberRef.currentRuntimeFlags     -> fiberRefs.getOrDefault(FiberRef.currentRuntimeFlags),
        FiberRef.currentSupervisors      -> fiberRefs.getOrDefault(FiberRef.currentSupervisors)
      )

    lazy val context: FiberContext[E, A] = new FiberContext[E, A](
      fiberId,
      StackBool(InterruptStatus.Interruptible.toBoolean),
      new java.util.concurrent.atomic.AtomicReference(fiberRefs.update(fiberId)(runtimeFiberRefs).fiberRefLocals),
      children
    )

    FiberScope.global.unsafeAdd(
      fiberRefs.getOrDefault(FiberRef.currentRuntimeFlags)(RuntimeFlag.EnableFiberRoots),
      context
    )

    fiberRefs.getOrDefault(FiberRef.currentSupervisors).foreach { supervisor =>
      supervisor.unsafeOnStart(environment, zio, None, context)

      context.unsafeOnDone((exit, _) => supervisor.unsafeOnEnd(exit.flatten, context))
    }

    context.nextEffect = zio
    context.run()
    context.unsafeOnDone { (exit, fiberRefs) =>
      k(exit.flatten, fiberRefs)
    }

    fiberId =>
      k =>
        unsafeRunWithRefs(context.interruptAs(fiberId), fiberRefs)(
          (exit: Exit[Nothing, Exit[E, A]], fiberRefs: FiberRefs) => k(exit.flatten, fiberRefs)
        )
  }
}

object Runtime extends RuntimePlatformSpecific {

  def addLogger(logger: ZLogger[String, Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentLoggers.locallyScopedWith(_ + logger))

  def addSupervisor(supervisor: Supervisor[Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentSupervisors.locallyScopedWith(_ + supervisor))

  val enableCurrentFiber: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentRuntimeFlags.locallyScopedWith(_ + RuntimeFlag.EnableCurrentFiber))
  }

  lazy val enableFiberRoots: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentRuntimeFlags.locallyScopedWith(_ + RuntimeFlag.EnableFiberRoots))
  }

  val logRuntime: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentRuntimeFlags.locallyScopedWith(_ + RuntimeFlag.LogRuntime))
  }

  def setBlockingExecutor(executor: Executor)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentBlockingExecutor.locallyScoped(executor))

  def setExecutor(executor: Executor)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentExecutor.locallyScoped(executor))

  def setReportFatal(reportFatal: Throwable => Nothing)(implicit trace: Trace): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped(FiberRef.currentReportFatal.locallyScoped(reportFatal))

  lazy val superviseOperations: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentRuntimeFlags.locallyScopedWith(_ + RuntimeFlag.SuperviseOperations))
  }

  /**
   * A layer that adds a supervisor that tracks all forked fibers in a set. Note
   * that this may have a negative impact on performance.
   */
  def track(weak: Boolean)(implicit trace: Trace): ZLayer[Any, Nothing, Any] =
    addSupervisor(Supervisor.unsafeTrack(weak))

  val trackRuntimeMetrics: ZLayer[Any, Nothing, Unit] = {
    implicit val trace = Trace.empty
    ZLayer.scoped(FiberRef.currentRuntimeFlags.locallyScopedWith(_ + RuntimeFlag.TrackRuntimeMetrics))
  }

  private[zio] type UnsafeSuccess <: AnyRef
  private[zio] class Lazy[A](thunk: () => A) {
    lazy val value = thunk()
  }
  private[zio] object Lazy {
    def apply[A](a: => A): Lazy[A] = new Lazy(() => a)

    def stackTraceBuilder[A](): Lazy[StackTraceBuilder] =
      new Lazy(() => StackTraceBuilder.unsafeMake())
  }

  class Proxy[+R](underlying: Runtime[R]) extends Runtime[R] {
    def environment        = underlying.environment
    override def fiberRefs = underlying.fiberRefs
  }

  /**
   * A runtime that can be shutdown to release resources allocated to it.
   */
  abstract class Scoped[+R] extends Runtime[R] {

    /**
     * Shuts down this runtime and releases resources allocated to it. Once this
     * runtime has been shut down the behavior of methods on it is undefined and
     * it should be discarded.
     */
    def shutdown(): Unit

    override final def as[R1](r1: ZEnvironment[R1]): Runtime.Scoped[R1] =
      map(_ => r1)

    override final def map[R1](f: ZEnvironment[R] => ZEnvironment[R1]): Runtime.Scoped[R1] =
      Scoped(f(environment), fiberRefs, () => shutdown())
  }

  object Scoped {

    /**
     * Builds a new scoped runtime given an environment `R`, a
     * [[zio.FiberRefs]], and a shut down action.
     */
    def apply[R](
      r: ZEnvironment[R],
      fiberRefs0: FiberRefs = FiberRefs.empty,
      shutdown0: () => Unit
    ): Runtime.Scoped[R] =
      new Runtime.Scoped[R] {
        val environment        = r
        def shutdown()         = shutdown0()
        override val fiberRefs = fiberRefs0
      }
  }

  /**
   * Builds a new runtime given an environment `R` and a [[zio.FiberRefs]].
   */
  def apply[R](r: ZEnvironment[R], fiberRefs0: FiberRefs): Runtime[R] =
    new Runtime[R] {
      val environment        = r
      override val fiberRefs = fiberRefs0
    }

  /**
   * The default [[Runtime]] for most ZIO applications. This runtime is
   * configured with the the default runtime configuration, which is optimized
   * for typical ZIO applications.
   */
  val default: Runtime[Any] =
    Runtime(ZEnvironment.empty, FiberRefs.empty)

  /**
   * Unsafely creates a `Runtime` from a `ZLayer` whose resources will be
   * allocated immediately, and not released until the `Runtime` is shut down or
   * the end of the application.
   *
   * This method is useful for small applications and integrating ZIO with
   * legacy code, but other applications should investigate using
   * [[ZIO.provide]] directly in their application entry points.
   */
  def unsafeFromLayer[R](layer: Layer[Any, R])(implicit trace: Trace): Runtime.Scoped[R] = {
    val (runtime, shutdown) = default.unsafeRun {
      Scope.make.flatMap { scope =>
        scope.extend(layer.toRuntime).flatMap { acquire =>
          val finalizer = () =>
            default.unsafeRun {
              scope.close(Exit.unit).uninterruptible.unit
            }

          ZIO.succeed(Platform.addShutdownHook(finalizer)).as((acquire, finalizer))
        }
      }
    }

    Runtime.Scoped(runtime.environment, runtime.fiberRefs, () => shutdown)
  }
}
