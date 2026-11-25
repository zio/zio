package zio

import zio.ZIO.Async
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.targetName

private[zio] transparent trait ZIOCompanionVersionSpecific {

  /**
   * Converts an asynchronous, callback-style API into a ZIO effect, which will
   * be executed asynchronously.
   *
   * This method allows you to specify the fiber id that is responsible for
   * invoking callbacks provided to the `register` function. This is called the
   * "blocking fiber", because it is stopping the fiber executing the async
   * effect from making progress (although it is not "blocking" a thread).
   * Specifying this fiber id in cases where it is known will improve
   * diagnostics, but not affect the behavior of the returned effect.
   *
   * On Scala 3, `register` runs with a given `Unsafe` in scope.
   *
   * The returned effect completes with the effect that is passed to the
   * callback.
   *
   * {{{
   * def fetchQuote(symbol: String, onDone: Either[Throwable, BigDecimal] => Unit): Unit =
   *   onDone(Right(BigDecimal("101.25")))
   *
   * val quote: Task[BigDecimal] =
   *   ZIO.async[Any, Throwable, BigDecimal] { callback =>
   *     fetchQuote(
   *       "ACME",
   *       {
   *         case Right(price) => callback(ZIO.succeed(price))
   *         case Left(error)  => callback(ZIO.fail(error))
   *       }
   *     )
   *   }
   * // succeeds with 101.25
   * }}}
   */
  def async[R, E, A](
    register: Unsafe ?=> (ZIO[R, E, A] => Unit) => Unit,
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    Async(
      trace,
      { k =>
        register(using Unsafe)(k)
        null
      },
      () => blockingOn
    )

  /**
   * Converts an asynchronous, callback-style API into a ZIO effect, which will
   * be executed asynchronously.
   *
   * With this variant, you can specify either a way to cancel the asynchrounous
   * action, or you can return the result right away if no asynchronous
   * operation is required.
   *
   * This method allows you to specify the fiber id that is responsible for
   * invoking callbacks provided to the `register` function. This is called the
   * "blocking fiber", because it is stopping the fiber executing the async
   * effect from making progress (although it is not "blocking" a thread).
   * Specifying this fiber id in cases where it is known will improve
   * diagnostics, but not affect the behavior of the returned effect.
   *
   * On Scala 3, `register` runs with a given `Unsafe` in scope.
   */
  def asyncInterrupt[R, E, A](
    register: Unsafe ?=> (ZIO[R, E, A] => Unit) => Either[URIO[R, Any], ZIO[R, E, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.Async[R, E, A](trace, register(using Unsafe), () => blockingOn)

  /**
   * Converts an asynchronous, callback-style API into a ZIO effect, which will
   * be executed asynchronously.
   *
   * With this variant, the registration function may return the result right
   * away, if it turns out that no asynchronous operation is required to
   * complete the operation.
   *
   * This method allows you to specify the fiber id that is responsible for
   * invoking callbacks provided to the `register` function. This is called the
   * "blocking fiber", because it is stopping the fiber executing the async
   * effect from making progress (although it is not "blocking" a thread).
   * Specifying this fiber id in cases where it is known will improve
   * diagnostics, but not affect the behavior of the returned effect.
   *
   * On Scala 3, `register` runs with a given `Unsafe` in scope.
   */
  def asyncMaybe[R, E, A](
    register: Unsafe ?=> (ZIO[R, E, A] => Unit) => Option[ZIO[R, E, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: Trace): ZIO[R, E, A] =
    Async(
      trace,
      register(using Unsafe)(_) match {
        case Some(value) => Right(value)
        case _           => null
      },
      () => blockingOn
    )

  /**
   * Returns an effect that, when executed, will cautiously run the provided
   * code, catching any non-fatal exception (any `Throwable` except a
   * `VirtualMachineError`) and translating it into a failed ZIO effect.
   *
   * This method should be used whenever you want to take arbitrary code, which
   * may throw exceptions or not be type safe, and convert it into a ZIO effect,
   * which can safely execute that code whenever the effect is executed.
   *
   * On Scala 3, `code` runs with a given `Unsafe` in scope.
   *
   * {{{
   * def printLine(line: String): Task[Unit] = ZIO.attempt(println(line))
   *
   * val port: Task[Int] = ZIO.attempt("80a0".toInt)
   * // fails with java.lang.NumberFormatException: For input string: "80a0"
   * }}}
   */
  def attempt[A](code: Unsafe ?=> A)(implicit trace: Trace): Task[A] =
    ZIO.suspendSucceed {
      try {
        Exit.succeed(code(using Unsafe))
      } catch {
        case t if nonFatal(t) => ZIO.failCause(Cause.fail(t))
      }
    }

  /**
   * Returns an effect that, when executed, will cautiously run the provided
   * code, catching any non-fatal exception (any `Throwable` except a
   * `VirtualMachineError`) and translating it into a failed ZIO effect.
   *
   * This method should be used whenever you want to take arbitrary code, which
   * may throw exceptions or not be type safe, and convert it into a ZIO effect,
   * which can safely execute that code whenever the effect is executed.
   *
   * This variant expects that the provided code will engage in blocking I/O,
   * and therefore, pro-actively executes the code on a dedicated blocking
   * thread pool, so it won't interfere with the main thread pool that ZIO uses.
   * On Scala.js there is no separate blocking pool, and the code runs on the
   * default executor.
   *
   * On Scala 3, `effect` runs with a given `Unsafe` in scope.
   *
   * {{{
   * // stands in for a blocking JDBC call
   * def lookupCustomer(id: Int): String = "customer-" + id
   *
   * val customer: Task[String] = ZIO.attemptBlocking(lookupCustomer(42))
   * // succeeds with "customer-42", computed on a blocking thread on the JVM
   * }}}
   */
  def attemptBlocking[A](effect: Unsafe ?=> A)(implicit trace: Trace): Task[A] =
    ZIO.blocking(ZIO.attempt(effect))

  /**
   * Returns an effect that, when executed, will cautiously run the provided
   * code, catching any non-fatal exception (any `Throwable` except a
   * `VirtualMachineError`) and translating it into a failed ZIO effect.
   *
   * This method should be used whenever you want to take arbitrary code, which
   * may throw exceptions or not be type safe, and convert it into a ZIO effect,
   * which can safely execute that code whenever the effect is executed.
   *
   * This variant expects that the provided code will engage in blocking I/O,
   * and therefore, pro-actively executes the code on a dedicated blocking
   * thread pool, so it won't interfere with the main thread pool that ZIO uses.
   * On Scala.js there is no separate blocking pool, and the code runs on the
   * default executor.
   *
   * Additionally, this variant allows you to specify an effect that will cancel
   * the blocking operation. The code runs on a forked fiber, and `cancel` runs
   * when the fiber that runs the returned effect is interrupted while it waits
   * for that code.
   */
  def attemptBlockingCancelable[R, A](effect: => A)(cancel: => URIO[R, Any])(implicit trace: Trace): RIO[R, A] =
    ZIO.blocking(ZIO.attempt(effect)).fork.flatMap(_.join).onInterrupt(cancel)

  /**
   * This function is the same as `attemptBlocking`, except that it only
   * exposes `IOException`: any other exception becomes a defect, so the effect
   * dies.
   *
   * On Scala 3, `effect` runs with a given `Unsafe` in scope.
   */
  def attemptBlockingIO[A](effect: Unsafe ?=> A)(implicit trace: Trace): IO[IOException, A] =
    attemptBlocking(effect).refineToOrDie[IOException]

  /**
   * Wraps the provided effect in a catch-try block. Useful for handling cases
   * where the user-provided effect might throw outside the ZIO effect, but we
   * don't want to incur the performance penalty from the additional flatMap in
   * `ZIO.suspend`.
   */
  inline protected def attemptOrDieZIO[R, E, A](inline effect: ZIO[R, E, A])(using Trace): ZIO[R, E, A] =
    try effect
    catch {
      case t if nonFatal(t) => Exit.die(t)
    }

  /**
   * Returns an effect that, when executed, will cautiously run the provided
   * code, ignoring its result and any non-fatal exception it throws (any
   * `Throwable` except a `VirtualMachineError`).
   *
   * On Scala 3, `code` runs with a given `Unsafe` in scope.
   */
  def ignore(code: Unsafe ?=> Any)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      try { code(using Unsafe); () }
      catch {
        case t if nonFatal(t) => ()
      }
    }

  /**
   * Returns an effect that models success with the specified value.
   *
   * `a` is evaluated each time the effect runs. When evaluating it throws, the
   * effect dies with the exception.
   *
   * On Scala 3, `a` runs with a given `Unsafe` in scope.
   *
   * {{{
   * val port: UIO[Int] = ZIO.succeed(8080)
   * // succeeds with 8080
   * }}}
   */
  inline def succeed[A](inline a: Unsafe ?=> A)(implicit inline trace: Trace): ZIO[Any, Nothing, A] =
    ZIO.Sync(trace, () => a(using Unsafe))

  /**
   * Returns a synchronous effect that does blocking and succeeds with the
   * specified value.
   *
   * On Scala 3, `a` runs with a given `Unsafe` in scope.
   */
  inline def succeedBlocking[A](inline a: Unsafe ?=> A)(implicit trace: Trace): UIO[A] =
    ZIO.blocking(ZIO.succeed(a))

  @targetName("succeed")
  @deprecated("use succeed", "2.1.7")
  private[zio] def _succeedCompat[A](a: Unsafe ?=> A)(implicit trace: Trace): ZIO[Any, Nothing, A] =
    succeed(a)

  @targetName("succeedBlocking")
  @deprecated("use succeedBlocking", "2.1.7")
  private[zio] def _succeedBlockingCompat[A](a: Unsafe ?=> A)(implicit trace: Trace): UIO[A] =
    ZIO.blocking(ZIO.succeed(a))

}
