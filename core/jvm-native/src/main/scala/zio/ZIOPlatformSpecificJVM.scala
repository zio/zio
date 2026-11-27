package zio

import zio.interop.javaz

import java.nio.channels.CompletionHandler
import java.util.concurrent.{CompletableFuture, CompletionStage, Future}

/**
 * JVM and Scala Native constructors of `ZIO` for Java interop.
 */
private[zio] trait ZIOPlatformSpecificJVM {

  /**
   * Imports a callback-style API that takes a `CompletionHandler` into a
   * `Task`. `op` is called each time the effect runs and receives the handler:
   * `completed` succeeds the task with the result, and `failed` fails it with
   * the exception (a `VirtualMachineError` becomes a defect instead). An
   * exception thrown by `op` fails the task. The fiber can be interrupted while
   * it waits for the handler.
   */
  def asyncWithCompletionHandler[T](op: CompletionHandler[T, Any] => Any)(implicit trace: Trace): Task[T] =
    javaz.asyncWithCompletionHandler(op)

  /**
   * Imports a `CompletionStage` into a `Task`. The `cs` thunk is evaluated each
   * time the effect runs, and an exception that it throws fails the task. When
   * the stage completes with a failure, the task fails with the exception,
   * unwrapped from a `CompletionException` or `ExecutionException`; a cancelled
   * stage interrupts the fiber. Interrupting the fiber while it waits cancels
   * the `CompletableFuture` of the stage.
   */
  def fromCompletionStage[A](cs: => CompletionStage[A])(implicit trace: Trace): Task[A] =
    javaz.fromCompletionStage(cs)

  /**
   * Alias for `fromCompletionStage` that takes a `CompletableFuture`.
   */
  def fromCompletableFuture[A](cs: => CompletableFuture[A])(implicit trace: Trace): Task[A] =
    fromCompletionStage(cs)

  /**
   * Imports a `java.util.concurrent.Future` into a `Task`. When the future is
   * already done, its result is returned without blocking, and a failure is
   * unwrapped from an `ExecutionException`. A future that is a
   * `CompletionStage` is handled like `fromCompletionStage`, without blocking.
   * For any other future, the effect blocks a thread of the blocking pool in
   * `Future#get` until the future completes, and interrupting the fiber takes
   * effect after `get` returns. A cancelled future interrupts the fiber. Prefer
   * `fromCompletionStage` when the source is a `CompletionStage`.
   */
  def fromFutureJava[A](future: => Future[A])(implicit trace: Trace): Task[A] = javaz.fromFutureJava(future)

  /**
   * Lifts a value of `A`, converting it into an error as an option in the error
   * channel when its value is `null`, making it easier to interop with Java
   * code. `v` is evaluated each time the effect runs, and an exception that it
   * throws is a defect.
   */
  final def fromNullable[A](v: => A)(implicit trace: Trace): IO[Option[Nothing], A] =
    ZIO.suspendSucceed {
      val v0 = v
      if (v0 == null) Exit.failNone
      else Exit.succeed(v0)
    }

}
