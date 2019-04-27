package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.internal.{Executor, Platform}

import scala.concurrent.ExecutionContext

/*
 * PHASE 1 DONE
 * PHASE 2 DONE
 * PHASE 3 DONE
 * PHASE 4 DONE
 * PHASE 6 DONE
 */

/*
 * EXTENDS ZIO_E_Throwable
 */
object Task {

  def apply[A](a: => A): Task[A] = effect(a)

  type TaskE = Throwable
  type TaskR = Any

  /*
   * ----------------------------------------------------------------------------------------------------------------------
   * The Following is taken from ZIOFunctions.
   * ----------------------------------------------------------------------------------------------------------------------
   */
  //  // ALL error types in this trait must be a subtype of `UpperE`.
  //  type UpperE = Throwable
  //  // ALL environment types in this trait must be a supertype of `LowerR`.
  //  type LowerR = Any

  /** Returns an effect that models failure with the specified error.
    * The moral equivalent of `throw` for pure code.
    * [[scalaz.zio.ZIO.fail]]
    */
  final def fail(error: TaskE): IO[TaskE, Nothing] = ZIO.fail(error)

  /** Returns an effect that models failure with the specified `Cause`.
    *
    *  [[scalaz.zio.ZIO.halt]]
    */
  final def halt(cause: Cause[TaskE]): IO[TaskE, Nothing] = ZIO.halt(cause)

  /** Returns an effect that models success with the specified strictly-
    * evaluated value.
    *
    * [[scalaz.zio.ZIO.succeed]]
    */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /** Returns an effect that models success with the specified lazily-evaluated
    * value. This method should not be used to capture effects. See
    * `[[ZIO.effectTotal]]` for capturing total effects, and `[[ZIO.effect]]` for capturing
    * partial effects.
    *
    * [[scalaz.zio.ZIO.succeedLazy]]
    */
  final def succeedLazy[A](a: => A): UIO[A] = ZIO.succeedLazy(a)

  /** Accesses the whole environment of the effect.
    *
    *  [[scalaz.zio.ZIO.environment]]
    */
  final def environment: ZIO[TaskR, Nothing, Any] = ZIO.access(ZIO.identityFn[TaskR])

  /** Accesses the environment of the effect.
    * {{{
    * val portNumber = effect.access(_.config.portNumber)
    * }}}
    *
    * [[scalaz.zio.ZIO.access]]
    */
  final def access: ZIO.AccessPartiallyApplied[Any] =
    ZIO.access

  /** Effectfully accesses the environment of the effect.
    *
    *  [[scalaz.zio.ZIO.accessM]]
    */
  final def accessM: ZIO.AccessMPartiallyApplied[Any] =
    ZIO.accessM

  /** Given an environment `R`, returns a function that can supply the
    * environment to programs that require it, removing their need for any
    * specific environment.
    *
    * This is similar to dependency injection, and the `provide` function can be
    * thought of as `inject`.
    *
    * [[scalaz.zio.ZIO.provide]]
    */
  final def provide[A](r: Any): ZIO[TaskR, TaskE, A] => IO[TaskE, A] =
    ZIO.provide(r)

  /** Returns an effect that accesses the runtime, which can be used to
    * (unsafely) execute tasks. This is useful for integration with
    * non-functional code that must call back into functional code.
    *
    * [[scalaz.zio.ZIO.runtime]]
    */
  final def runtime: ZIO[TaskR, Nothing, Runtime[TaskR]] = ZIO.runtime

  /** Returns an effect that is interrupted.
    *
    *  [[scalaz.zio.ZIO.interrupt]]
    */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /** Returns a effect that will never produce anything. The moral
    * equivalent of `while(true) {}`, only without the wasted CPU cycles.
    *
    * [[scalaz.zio.ZIO.never]]
    */
  final val never: UIO[Nothing] = ZIO.never

  /** Returns an effect that dies with the specified `Throwable`.
    * This method can be used for terminating a fiber because a defect has been
    * detected in the code.
    *
    * [[scalaz.zio.ZIO.die]]
    */
  final def die(t: TaskE): UIO[Nothing] = ZIO.die(t)

  /** Returns an effect that dies with a [[java.lang.RuntimeException]] having the
    * specified text message. This method can be used for terminating a fiber
    * because a defect has been detected in the code.
    *
    * [[scalaz.zio.ZIO.die]]
    */
  final def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /** Imports a total synchronous effect into a pure `ZIO` value.
    * The effect must not throw any exceptions. If you wonder if the effect
    * throws exceptions, then do not use this method, use [[Task.effect]],
    * [[IO.effect]], or [[ZIO.effect]].
    *
    * {{{
    * val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime())
    * }}}
    *
    * [[scalaz.zio.ZIO.effectTotal]]
    */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /** Imports a total synchronous effect into a pure `ZIO` value. This variant
    * of `effectTotal` lets the impure code use the platform capabilities.
    *
    * The effect must not throw any exceptions. If you wonder if the effect
    * throws exceptions, then do not use this method, use [[Task.effect]],
    * [[IO.effect]], or [[ZIO.effect]].
    *
    * {{{
    * val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime())
    * }}}
    *
    * [[scalaz.zio.ZIO.effectTotalWith]]
    */
  final def effectTotalWith[A](effect: Platform => A): UIO[A] = ZIO.effectTotalWith(effect)

  /** Returns an effect that yields to the runtime system, starting on a fresh
    * stack. Manual use of this method can improve fairness, at the cost of
    * overhead.
    *
    * [[scalaz.zio.ZIO.yieldNow]]
    */
  final val yieldNow: UIO[Unit] = ZIO.Yield

  /** Returns an effect that forks all of the specified values, and returns a
    * composite fiber that produces a list of their results, in order.
    *
    * [[scalaz.zio.ZIO.forkAll]]
    */
  final def forkAll[A](as: Iterable[ZIO[TaskR, TaskE, A]]): ZIO[TaskR, Nothing, Fiber[TaskE, List[A]]] =
    ZIO.forkAll(as)

  /** Returns an effect that forks all of the specified values, and returns a
    * composite fiber that produces unit. This version is faster than [[forkAll]]
    * in cases where the results of the forked fibers are not needed.
    *
    * [[scalaz.zio.ZIO.forkAll_]]
    */
  final def forkAll_[A](as: Iterable[ZIO[TaskR, TaskE, A]]): ZIO[TaskR, Nothing, Unit] =
    ZIO.forkAll_(as)

  /** Returns an effect from a [[scalaz.zio.Exit]] value.
    *
    *  [[scalaz.zio.ZIO.done]]
    */
  final def done[A](r: Exit[TaskE, A]): IO[TaskE, A] = ZIO.done(r)

  /** Enables supervision for this effect. This will cause fibers forked by
    * this effect to be tracked and will enable their inspection via [[ZIO.children]].
    *
    * [[scalaz.zio.ZIO.supervised]]
    */
  final def supervised[A](zio: ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.supervised(zio)

  /** Returns an effect that supervises the specified effect, ensuring that all
    * fibers that it forks are interrupted as soon as the supervised effect
    * completes.
    *
    * [[scalaz.zio.ZIO.supervise]]
    */
  final def supervise[A](zio: ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.supervise(zio)

  /** Returns an effect that supervises the specified effect, ensuring that all
    * fibers that it forks are passed to the specified supervisor as soon as the
    * supervised effect completes.
    *
    * [[scalaz.zio.ZIO.superviseWith]]
    */
  final def superviseWith[A](zio: ZIO[TaskR, TaskE, A])(supervisor: IndexedSeq[Fiber[_, _]] => ZIO[TaskR, Nothing, _]): ZIO[TaskR, TaskE, A] =
    ZIO.superviseWith(zio)(supervisor)

  /** Returns an effect that first executes the outer effect, and then executes
    * the inner effect, returning the value from the inner effect, and effectively
    * flattening a nested effect.
    *
    * [[scalaz.zio.ZIO.flatten]]
    */
  final def flatten[A](zio: ZIO[TaskR, TaskE, ZIO[TaskR, TaskE, A]]): ZIO[TaskR, TaskE, A] =
    ZIO.flatten(zio)

  /** Returns a lazily constructed effect, whose construction may itself require
    * effects. This is a shortcut for `flatten(effectTotal(io))`.
    *
    * [[scalaz.zio.ZIO.suspend]]
    */
  final def suspend[A](io: => ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.suspend(io)

  /** Returns an effect that will execute the specified effect fully on the
    * provided executor, before returning to the default executor.
    *
    * [[scalaz.zio.ZIO.lock]]
    */
  final def lock[A](executor: Executor)(zio: ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.lock(executor)(zio)

  /** Imports an asynchronous effect into a pure `ZIO` value. See `effectAsyncMaybe` for
    * the more expressive variant of this function that can return a value
    * synchronously.
    *
    * [[scalaz.zio.ZIO.effectAsync]]
    */
  final def effectAsync[A](register: (ZIO[TaskR, TaskE, A] => Unit) => Unit): ZIO[TaskR, TaskE, A] =
    ZIO.effectAsync(register)

  /** Imports an asynchronous effect into a pure `ZIO` value, possibly returning
    * the value synchronously.
    *
    * [[scalaz.zio.ZIO.effectAsyncMaybe]]
    */
  final def effectAsyncMaybe[A](register: (ZIO[TaskR, TaskE, A] => Unit) => Option[IO[TaskE, A]]): ZIO[TaskR, TaskE, A] =
    ZIO.effectAsyncMaybe(register)

  /** Imports an asynchronous effect into a pure `ZIO` value. This formulation is
    * necessary when the effect is itself expressed in terms of `ZIO`.
    */
  final def effectAsyncM[A](register: (ZIO[TaskR, TaskE, A] => Unit) => ZIO[TaskR, Nothing, _]): ZIO[TaskR, TaskE, A] =
    ZIO.effectAsyncM(register)

  /** Imports an asynchronous effect into a pure `IO` value. The effect has the
    * option of returning the value synchronously, which is useful in cases
    * where it cannot be determined if the effect is synchronous or asynchronous
    * until the effect is actually executed. The effect also has the option of
    * returning a canceler, which will be used by the runtime to cancel the
    * asynchronous effect if the fiber executing the effect is interrupted.
    */
  final def effectAsyncInterrupt[A](register: (ZIO[TaskR, TaskE, A] => Unit) => Either[Canceler, ZIO[TaskR, TaskE, A]]): ZIO[TaskR, TaskE, A] =
    ZIO.effectAsyncInterrupt(register)

  /** Submerges the error case of an `Either` into the `ZIO`. The inverse
    * operation of `IO.either`.
    */
  final def absolve[A](v: ZIO[TaskR, TaskE, Either[TaskE, A]]): ZIO[TaskR, TaskE, A] =
    ZIO.absolve(v)

  /** The inverse operation `IO.sandboxed`
    *
    * Terminates with exceptions on the `Left` side of the `Either` error, if it
    * exists. Otherwise extracts the contained `IO[E, A]`
    */
  final def unsandbox[A](v: ZIO[TaskR, Cause[TaskE], A]): ZIO[TaskR, TaskE, A] = ZIO.unsandbox(v)

  /** Returns the identity effectful function, which performs no effects
    */
  final def identity: ZIO[TaskR, Nothing, Any] = ZIO.identity

  /** Returns an effectful function that merely swaps the elements in a `Tuple2`.
    */
  final def swap[A, B](implicit ev: TaskR <:< (A, B)): ZIO[TaskR, TaskE, (B, A)] =
    ZIO.swap

  /** Returns an effectful function that extracts out the first element of a
    * tuple.
    */
  final def _1[A, B](implicit ev: TaskR <:< (A, B)): ZIO[TaskR, TaskE, A] = ZIO._1

  /** Returns an effectful function that extracts out the second element of a
    * tuple.
    */
  final def _2[A, B](implicit ev: TaskR <:< (A, B)): ZIO[TaskR, TaskE, B] = ZIO._2

  /** Lifts a function `R => A` into a `ZIO[R, Nothing, A]`.
    */
  final def fromFunction[A](f: TaskR => A): ZIO[TaskR, Nothing, A] =
    ZIO.fromFunction(f)

  /** Lifts an effectful function whose effect requires no environment into
    * an effect that requires the input to the function.
    *
    * Q: Should this function remain as it is, or should the E be converted to Throwable
    */
  final def fromFunctionM[A](f: Any => IO[TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.fromFunctionM(f)

  /** Lifts an `Either` into a `ZIO` value.
    *
    *  Q: Should this function remain as it is, or should the E be converted to Throwable
    */
  final def fromEither[A](v: => Either[TaskE, A]): IO[TaskE, A] =
    ZIO.fromEither(v)

  /** Creates a `ZIO` value that represents the exit value of the specified
    * fiber.
    *
    * Q: Should this function remain as it is, or should the E be converted to Throwable
    */
  final def fromFiber[A](fiber: => Fiber[TaskE, A]): IO[TaskE, A] =
    ZIO.fromFiber(fiber)

  /** Creates a `ZIO` value that represents the exit value of the specified
    * fiber.
    *
    * Q: Should this function remain as it is, or should the E be converted to Throwable
    */
  final def fromFiberM[A](fiber: IO[TaskE, Fiber[TaskE, A]]): IO[TaskE, A] =
    ZIO.fromFiberM(fiber)

  /** Requires that the given `IO[E, Option[A]]` contain a value. If there is no
    * value, then the specified error will be raised.
    */
  final def require[A](error: TaskE): IO[TaskE, Option[A]] => IO[TaskE, A] =
    ZIO.require(error)

  /** When this effect represents acquisition of a resource (for example,
    * opening a file, launching a thread, etc.), `bracket` can be used to ensure
    * the acquisition is not interrupted and the resource is always released.
    *
    * The function does two things:
    *
    * 1. Ensures this effect, which acquires the resource, will not be
    * interrupted. Of course, acquisition may fail for internal reasons (an
    * uncaught exception).
    * 2. Ensures the `release` effect will not be interrupted, and will be
    * executed so long as this effect successfully acquires the resource.
    *
    * In between acquisition and release of the resource, the `use` effect is
    * executed.
    *
    * If the `release` effect fails, then the entire effect will fail even
    * if the `use` effect succeeds. If this fail-fast behavior is not desired,
    * errors produced by the `release` effect can be caught and ignored.
    *
    * {{{
    * openFile("data.json").bracket(closeFile) { file =>
    *   for {
    *     header <- readHeader(file)
    *     ...
    *   } yield result
    * }
    * }}}
    */
  final def bracket[A](acquire: ZIO[TaskR, TaskE, A]): ZIO.BracketAcquire[TaskR, TaskE, A] =
    ZIO.bracket(acquire)

  /** Uncurried version. Doesn't offer curried syntax and have worse type-inference
    * characteristics, but guarantees no extra allocations of intermediate
    * [[scalaz.zio.ZIO.BracketAcquire]] and [[scalaz.zio.ZIO.BracketRelease]] objects.
    */
  final def bracket[A, B](acquire: ZIO[TaskR, TaskE, A],
      release: A => ZIO[TaskR, Nothing, _],
      use: A => ZIO[TaskR, TaskE, B]): ZIO[TaskR, TaskE, B] = ZIO.bracket(acquire, release, use)

  /** Acquires a resource, uses the resource, and then releases the resource.
    * Neither the acquisition nor the release will be interrupted, and the
    * resource is guaranteed to be released, so long as the `acquire` effect
    * succeeds. If `use` fails, then after release, the returned effect will fail
    * with the same error.
    */
  final def bracketExit[A](acquire: ZIO[TaskR, TaskE, A]): ZIO.BracketExitAcquire[TaskR, TaskE, A] =
    ZIO.bracketExit(acquire)

  /** Uncurried version. Doesn't offer curried syntax and have worse type-inference
    * characteristics, but guarantees no extra allocations of intermediate
    * [[scalaz.zio.ZIO.BracketExitAcquire]] and [[scalaz.zio.ZIO.BracketExitRelease]] objects.
    */
  final def bracketExit[A, B](acquire: ZIO[TaskR, TaskE, A],
      release: (A, Exit[TaskE, B]) => ZIO[TaskR, Nothing, _],
      use: A => ZIO[TaskR, TaskE, B]): ZIO[TaskR, TaskE, B] =
    ZIO.bracketExit(acquire, release, use)

  /** Applies the function `f` to each element of the `Iterable[A]` and
    * returns the results in a new `List[B]`.
    *
    * For a parallel version of this method, see `foreachPar`.
    */
  final def foreach[A, B](in: Iterable[A])(f: A => ZIO[TaskR, TaskE, B]): ZIO[TaskR, TaskE, List[B]] =
    ZIO.foreach(in)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` in parallel,
    * and returns the results in a new `List[B]`.
    *
    * For a sequential version of this method, see `foreach`.
    */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => ZIO[TaskR, TaskE, B]): ZIO[TaskR, TaskE, List[B]] =
    ZIO.foreachPar(as)(fn)

  /** Applies the function `f` to each element of the `Iterable[A]` in parallel,
    * and returns the results in a new `List[B]`.
    *
    * Unlike `foreachPar`, this method will use at most up to `n` fibers.
    */
  final def foreachParN[A, B](n: Long)(as: Iterable[A])(fn: A => ZIO[TaskR, TaskE, B]): ZIO[TaskR, TaskE, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects sequentially.
    *
    * Equivalent to `foreach(as)(f).void`, but without the cost of building
    * the list of results.
    */
  final def foreach_[A](as: Iterable[A])(f: A => ZIO[TaskR, TaskE, _]): ZIO[TaskR, TaskE, Unit] =
    ZIO.foreach_(as)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects in parallel, discarding the results.
    *
    * For a sequential version of this method, see `foreach_`.
    */
  final def foreachPar_[A, B](as: Iterable[A])(f: A => ZIO[TaskR, TaskE, _]): ZIO[TaskR, TaskE, Unit] =
    ZIO.foreachPar_(as)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects in parallel, discarding the results.
    *
    * Unlike `foreachPar_`, this method will use at most up to `n` fibers.
    */
  final def foreachParN_[A, B](n: Long)(as: Iterable[A])(f: A => ZIO[TaskR, TaskE, _]): ZIO[TaskR, TaskE, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /** Evaluate each effect in the structure from left to right, and collect
    * the results. For a parallel version, see `collectAllPar`.
    */
  final def collectAll[A](in: Iterable[ZIO[TaskR, TaskE, A]]): ZIO[TaskR, TaskE, List[A]] =
    ZIO.collectAll(in)

  /** Evaluate each effect in the structure in parallel, and collect
    * the results. For a sequential version, see `collectAll`.
    */
  final def collectAllPar[A](as: Iterable[ZIO[TaskR, TaskE, A]]): ZIO[TaskR, TaskE, List[A]] =
    ZIO.collectAllPar(as)

  /** Evaluate each effect in the structure in parallel, and collect
    * the results. For a sequential version, see `collectAll`.
    *
    * Unlike `foreachAllPar`, this method will use at most `n` fibers.
    */
  final def collectAllParN[A](n: Long)(as: Iterable[ZIO[TaskR, TaskE, A]]): ZIO[TaskR, TaskE, List[A]] =
    ZIO.collectAllParN(n)(as)

  /** Races an `IO[E, A]` against zero or more other effects. Yields either the
    * first success or the last failure.
    *
    * R1? R?
    */
  final def raceAll[A](zio: ZIO[TaskR, TaskE, A],
      ios: Iterable[ZIO[TaskR, TaskE, A]]): ZIO[TaskR, TaskE, A] = ZIO.raceAll(zio, ios)

  /** Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
    */
  final def reduceAll[A](a: ZIO[TaskR, TaskE, A],
      as: Iterable[ZIO[TaskR, TaskE, A]])(f: (A, A) => A): ZIO[TaskR, TaskE, A] =
    ZIO.reduceAll(a, as)(f)

  /** Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
    */
  final def reduceAllPar[A](a: ZIO[TaskR, TaskE, A],
      as: Iterable[ZIO[TaskR, TaskE, A]])(f: (A, A) => A): ZIO[TaskR, TaskE, A] =
    ZIO.reduceAllPar(a, as)(f)

  /** Merges an `Iterable[IO]` to a single IO, working sequentially.
    */
  final def mergeAll[A, B](in: Iterable[ZIO[TaskR, TaskE, A]])(zero: B)(f: (B, A) => B): ZIO[TaskR, TaskE, B] =
    ZIO.mergeAll(in)(zero)(f)

  /** Merges an `Iterable[IO]` to a single IO, working in parallel.
    */
  final def mergeAllPar[A, B](in: Iterable[ZIO[TaskR, TaskE, A]])(zero: B)(f: (B, A) => B): ZIO[TaskR, TaskE, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /** Strictly-evaluated unit lifted into the `ZIO` monad.
    */
  final val unit: ZIO[Any, Nothing, Unit] = ZIO.unit

  /** The moral equivalent of `if (p) exp`
    */
  final def when(b: Boolean)(zio: ZIO[TaskR, TaskE, _]): ZIO[TaskR, TaskE, Unit] =
    ZIO.when(b)(zio)

  /** The moral equivalent of `if (p) exp` when `p` has side-effects
    */
  final def whenM(b: ZIO[TaskR, TaskE, Boolean])(zio: ZIO[TaskR, TaskE, _]): ZIO[TaskR, TaskE, Unit] =
    ZIO.whenM(b)(zio)

  /** Folds an `Iterable[A]` using an effectful function `f`, working sequentially.
    */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => ZIO[TaskR, TaskE, S]): ZIO[TaskR, TaskE, S] =
    ZIO.foldLeft(in)(zero)(f)

  /** Returns information about the current fiber, such as its identity.
    */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /** Constructs an effect based on information about the current fiber, such as
    * its identity.
    */
  final def descriptorWith[A](f: Fiber.Descriptor => ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.descriptorWith(f)

  /** Checks the interrupt status, and produces the effect returned by the
    * specified callback.
    */
  final def checkInterruptible[A](f: Boolean => ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.checkInterruptible(f)

  /** Makes an explicit check to see if the fiber has been interrupted, and if
    * so, performs self-interruption.
    */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /** Makes the effect uninterruptible, but passes it a restore function that
    * can be used to restore the inherited interruptibility from whatever region
    * the effect is composed into.
    */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.uninterruptibleMask(k)

  /** Makes the effect interruptible, but passes it a restore function that
    * can be used to restore the inherited interruptibility from whatever region
    * the effect is composed into.
    */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.interruptibleMask(k)

  /** Prefix form of `ZIO#uninterruptible`.
    */
  final def uninterruptible[A](zio: ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.uninterruptible(zio)

  /** Prefix form of `ZIO#interruptible`.
    */
  final def interruptible[A](zio: ZIO[TaskR, TaskE, A]): ZIO[TaskR, TaskE, A] =
    ZIO.interruptible(zio)

  /** Provides access to the list of child fibers supervised by this fiber.
    *
    * '''Note:''' supervision must be enabled (via [[ZIO#supervised]]) on the
    * current fiber for this operation to return non-empty lists.
    */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /** Acquires a resource, uses the resource, and then releases the resource.
    * However, unlike `bracket`, the separation of these phases allows
    * the acquisition to be interruptible.
    *
    * Useful for concurrent data structures and other cases where the
    * 'deallocator' can tell if the allocation succeeded or not just by
    * inspecting internal / external state.
    */
  def reserve[A, B](reservation: ZIO[TaskR, TaskE, Reservation[TaskR, TaskE, A]])(use: A => ZIO[TaskR, TaskE, B]): ZIO[TaskR, TaskE, B] =
    ZIO.reserve(reservation)(use)

  /*
  * ----------------------------------------------------------------------------------------------------------------------
  * The following is taken from ZIO_E_Throwable
  * ----------------------------------------------------------------------------------------------------------------------
  */

  /** Imports a synchronous effect into a pure `ZIO` value, translating any
    * throwables into a `Throwable` failure in the returned value.
    *
    * {{{
    * def putStrLn(line: String): Task[Unit] = Task.effect(println(line))
    * }}}
    */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /** Lifts a `Try` into a `ZIO`.
    */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /** Imports a function that creates a [[scala.concurrent.Future]] from an
    * [[scala.concurrent.ExecutionContext]] into a `ZIO`.
    */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)
}