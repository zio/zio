package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.internal.{ Executor, Platform }

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
object TaskR {

  type TaskrE = Throwable
  // type TaskrR = Polymorphic

  def apply[A](a: => A): Task[A] = effect(a)

  /*
   * ----------------------------------------------------------------------------------------------------------------------
   * The Following is taken from ZIOFunctions.
   * ----------------------------------------------------------------------------------------------------------------------
   */

  /** Returns an effect that models failure with the specified error.
    * The moral equivalent of `throw` for pure code.
    */
  final def fail(error: TaskrE): IO[TaskrE, Nothing] = ZIO.fail(error)

  /** Returns an effect that models failure with the specified `Cause`.
    */
  final def halt(cause: Cause[TaskrE]): IO[TaskrE, Nothing] = ZIO.halt(cause)

  /** Returns an effect that models success with the specified strictly-
    * evaluated value.
    */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /** Returns an effect that models success with the specified lazily-evaluated
    * value. This method should not be used to capture effects. See
    * `[[ZIO.effectTotal]]` for capturing total effects, and `[[ZIO.effect]]` for capturing
    * partial effects.
    */
  final def succeedLazy[A](a: => A): UIO[A] = ZIO.succeedLazy(a)

  /** Accesses the whole environment of the effect.
    */
  final def environment[R]: ZIO[R, Nothing, R] = ZIO.access(ZIO.identityFn[R])

  /** Accesses the environment of the effect.
    * {{{
    * val portNumber = effect.access(_.config.portNumber)
    * }}}
    */
  final def access[R]: ZIO.AccessPartiallyApplied[R] =
    ZIO.access

  /** Effectfully accesses the environment of the effect.
    */
  final def accessM[R]: ZIO.AccessMPartiallyApplied[R] =
    ZIO.accessM

  /** Given an environment `R`, returns a function that can supply the
    * environment to programs that require it, removing their need for any
    * specific environment.
    *
    * This is similar to dependency injection, and the `provide` function can be
    * thought of as `inject`.
    */
  final def provide[R, A](r: R): ZIO[R, TaskrE, A] => IO[TaskrE, A] =
    ZIO.provide(r)

  /** Returns an effect that accesses the runtime, which can be used to
    * (unsafely) execute tasks. This is useful for integration with
    * non-functional code that must call back into functional code.
    */
  final def runtime[R]: ZIO[R, Nothing, Runtime[R]] = ZIO.runtime

  /** Returns an effect that is interrupted.
    */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /** Returns a effect that will never produce anything. The moral
    * equivalent of `while(true) {}`, only without the wasted CPU cycles.
    */
  final val never: UIO[Nothing] = ZIO.never

  /** Returns an effect that dies with the specified `Throwable`.
    * This method can be used for terminating a fiber because a defect has been
    * detected in the code.
    */
  final def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /** Returns an effect that dies with a [[java.lang.RuntimeException]] having the
    * specified text message. This method can be used for terminating a fiber
    * because a defect has been detected in the code.
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
    */
  final def effectTotalWith[A](effect: Platform => A): UIO[A] = ZIO.effectTotalWith(effect)

  /** Returns an effect that yields to the runtime system, starting on a fresh
    * stack. Manual use of this method can improve fairness, at the cost of
    * overhead.
    */
  final val yieldNow: UIO[Unit] = ZIO.Yield

  /** Returns an effect that forks all of the specified values, and returns a
    * composite fiber that produces a list of their results, in order.
    */
  final def forkAll[R, A](as: Iterable[ZIO[R, TaskrE, A]]): ZIO[R, Nothing, Fiber[TaskrE, List[A]]] =
    ZIO.forkAll(as)

  /** Returns an effect that forks all of the specified values, and returns a
    * composite fiber that produces unit. This version is faster than [[forkAll]]
    * in cases where the results of the forked fibers are not needed.
    */
  final def forkAll_[R, A](as: Iterable[ZIO[R, TaskrE, A]]): ZIO[R, Nothing, Unit] =
    ZIO.forkAll_(as)

  /** Returns an effect from a [[scalaz.zio.Exit]] value.
    */
  final def done[A](r: Exit[TaskrE, A]): IO[TaskrE, A] = ZIO.done(r)

  /** Enables supervision for this effect. This will cause fibers forked by
    * this effect to be tracked and will enable their inspection via [[ZIO.children]].
    */
  final def supervised[R, A](zio: ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.supervised(zio)

  /** Returns an effect that supervises the specified effect, ensuring that all
    * fibers that it forks are interrupted as soon as the supervised effect
    * completes.
    */
  final def supervise[R, A](zio: ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.supervise(zio)

  /** Returns an effect that supervises the specified effect, ensuring that all
    * fibers that it forks are passed to the specified supervisor as soon as the
    * supervised effect completes.
    */
  final def superviseWith[R, A](zio: ZIO[R, TaskrE, A])(supervisor: IndexedSeq[Fiber[_, _]] => ZIO[R, Nothing, _]): ZIO[R, TaskrE, A] =
    ZIO.superviseWith(zio)(supervisor)

  /** Returns an effect that first executes the outer effect, and then executes
    * the inner effect, returning the value from the inner effect, and effectively
    * flattening a nested effect.
    */
  final def flatten[R, A](zio: ZIO[R, TaskrE, ZIO[R, TaskrE, A]]): ZIO[R, TaskrE, A] =
    ZIO.flatten(zio)

  /** Returns a lazily constructed effect, whose construction may itself require
    * effects. This is a shortcut for `flatten(effectTotal(io))`.
    */
  final def suspend[R, A](io: => ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.suspend(io)

  /** Returns an effect that will execute the specified effect fully on the
    * provided executor, before returning to the default executor.
    */
  final def lock[R, A](executor: Executor)(zio: ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.lock(executor)(zio)

  /** Imports an asynchronous effect into a pure `ZIO` value. See `effectAsyncMaybe` for
    * the more expressive variant of this function that can return a value
    * synchronously.
    */
  final def effectAsync[A](register: (ZIO[Any, TaskrE, A] => Unit) => Unit): ZIO[Any, TaskrE, A] =
    ZIO.effectAsync(register)

  /** Imports an asynchronous effect into a pure `ZIO` value, possibly returning
    * the value synchronously.
    */
  final def effectAsyncMaybe[A](register: (ZIO[Any, TaskrE, A] => Unit) => Option[IO[TaskrE, A]]): ZIO[Any, TaskrE, A] =
    ZIO.effectAsyncMaybe(register)

  /** Imports an asynchronous effect into a pure `ZIO` value. This formulation is
    * necessary when the effect is itself expressed in terms of `ZIO`.
    */
  final def effectAsyncM[R, A](register: (ZIO[R, TaskrE, A] => Unit) => ZIO[R, Nothing, _]): ZIO[R, TaskrE, A] =
    ZIO.effectAsyncM(register)

  /** Imports an asynchronous effect into a pure `IO` value. The effect has the
    * option of returning the value synchronously, which is useful in cases
    * where it cannot be determined if the effect is synchronous or asynchronous
    * until the effect is actually executed. The effect also has the option of
    * returning a canceler, which will be used by the runtime to cancel the
    * asynchronous effect if the fiber executing the effect is interrupted.
    */
  final def effectAsyncInterrupt[R, A](register: (ZIO[R, TaskrE, A] => Unit) => Either[Canceler, ZIO[R, TaskrE, A]]): ZIO[R, TaskrE, A] =
    ZIO.effectAsyncInterrupt(register)

  /** Submerges the error case of an `Either` into the `ZIO`. The inverse
    * operation of `IO.either`.
    */
  final def absolve[R, A](v: ZIO[R, TaskrE, Either[TaskrE, A]]): ZIO[R, TaskrE, A] =
    ZIO.absolve(v)

  /** The inverse operation `IO.sandboxed`
    *
    * Terminates with exceptions on the `Left` side of the `Either` error, if it
    * exists. Otherwise extracts the contained `IO[E, A]`
    */
  final def unsandbox[R, A](v: ZIO[R, Cause[TaskrE], A]): ZIO[R, TaskrE, A] = ZIO.unsandbox(v)

  /** Returns the identity effectful function, which performs no effects
    */
  final def identity[R]: ZIO[R, Nothing, R] = ZIO.identity

  /** Returns an effectful function that merely swaps the elements in a `Tuple2`.
    *
    *  Q: REMOVE implicit?
    */
  final def swap[R, A, B](implicit ev: R <:< (A, B)): ZIO[R, TaskrE, (B, A)] =
    ZIO.swap

  /** Returns an effectful function that extracts out the first element of a
    * tuple.
    */
  final def _1[R, A, B](implicit ev: R <:< (A, B)): ZIO[R, TaskrE, A] = ZIO._1

  /** Returns an effectful function that extracts out the second element of a
    * tuple.
    */
  final def _2[R, A, B](implicit ev: R <:< (A, B)): ZIO[R, TaskrE, B] = ZIO._2

  /** Lifts a function `R => A` into a `ZIO[R, Nothing, A]`.
    */
  final def fromFunction[R, A](f: R => A): ZIO[R, Nothing, A] =
    ZIO.fromFunction(f)

  /** Lifts an effectful function whose effect requires no environment into
    * an effect that requires the input to the function.
    *
    * Q: Keep [R, E, A] or leave it as is
    */
  final def fromFunctionM[R, A](f: R => IO[TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.fromFunctionM(f)

  /** Lifts an `Either` into a `ZIO` value.
    */
  final def fromEither[A](v: => Either[TaskrE, A]): IO[TaskrE, A] =
    ZIO.fromEither(v)

  /** Creates a `ZIO` value that represents the exit value of the specified
    * fiber.
    */
  final def fromFiber[A](fiber: => Fiber[TaskrE, A]): IO[TaskrE, A] =
    ZIO.fromFiber(fiber)

  /** Creates a `ZIO` value that represents the exit value of the specified
    * fiber.
    */
  final def fromFiberM[A](fiber: IO[TaskrE, Fiber[TaskrE, A]]): IO[TaskrE, A] =
    ZIO.fromFiberM(fiber)

  /** Requires that the given `IO[E, Option[A]]` contain a value. If there is no
    * value, then the specified error will be raised.
    */
  final def require[A](error: TaskrE): IO[TaskrE, Option[A]] => IO[TaskrE, A] =
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
  final def bracket[R, A](acquire: ZIO[R, TaskrE, A]): ZIO.BracketAcquire[R, TaskrE, A] =
    ZIO.bracket(acquire)

  /** Uncurried version. Doesn't offer curried syntax and have worse type-inference
    * characteristics, but guarantees no extra allocations of intermediate
    * [[scalaz.zio.ZIO.BracketAcquire]] and [[scalaz.zio.ZIO.BracketRelease]] objects.
    */
  final def bracket[R, A, B](acquire: ZIO[R, TaskrE, A],
      release: A => ZIO[R, Nothing, _],
      use: A => ZIO[R, TaskrE, B]): ZIO[R, TaskrE, B] = ZIO.bracket(acquire, release, use)

  /** Acquires a resource, uses the resource, and then releases the resource.
    * Neither the acquisition nor the release will be interrupted, and the
    * resource is guaranteed to be released, so long as the `acquire` effect
    * succeeds. If `use` fails, then after release, the returned effect will fail
    * with the same error.
    */
  final def bracketExit[R, A](acquire: ZIO[R, TaskrE, A]): ZIO.BracketExitAcquire[R, TaskrE, A] =
    ZIO.bracketExit(acquire)

  /** Uncurried version. Doesn't offer curried syntax and have worse type-inference
    * characteristics, but guarantees no extra allocations of intermediate
    * [[scalaz.zio.ZIO.BracketExitAcquire]] and [[scalaz.zio.ZIO.BracketExitRelease]] objects.
    */
  final def bracketExit[R, A, B](acquire: ZIO[R, TaskrE, A],
      release: (A, Exit[TaskrE, B]) => ZIO[R, Nothing, _],
      use: A => ZIO[R, TaskrE, B]): ZIO[R, TaskrE, B] =
    ZIO.bracketExit(acquire, release, use)

  /** Applies the function `f` to each element of the `Iterable[A]` and
    * returns the results in a new `List[B]`.
    *
    * For a parallel version of this method, see `foreachPar`.
    */
  final def foreach[R, A, B](in: Iterable[A])(f: A => ZIO[R, TaskrE, B]): ZIO[R, TaskrE, List[B]] =
    ZIO.foreach(in)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` in parallel,
    * and returns the results in a new `List[B]`.
    *
    * For a sequential version of this method, see `foreach`.
    */
  final def foreachPar[R, A, B](as: Iterable[A])(fn: A => ZIO[R, TaskrE, B]): ZIO[R, TaskrE, List[B]] =
    ZIO.foreachPar(as)(fn)

  /** Applies the function `f` to each element of the `Iterable[A]` in parallel,
    * and returns the results in a new `List[B]`.
    *
    * Unlike `foreachPar`, this method will use at most up to `n` fibers.
    */
  final def foreachParN[R, A, B](n: Long)(as: Iterable[A])(fn: A => ZIO[R, TaskrE, B]): ZIO[R, TaskrE, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects sequentially.
    *
    * Equivalent to `foreach(as)(f).void`, but without the cost of building
    * the list of results.
    */
  final def foreach_[R, A](as: Iterable[A])(f: A => ZIO[R, TaskrE, _]): ZIO[R, TaskrE, Unit] =
    ZIO.foreach_(as)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects in parallel, discarding the results.
    *
    * For a sequential version of this method, see `foreach_`.
    */
  final def foreachPar_[R, A, B](as: Iterable[A])(f: A => ZIO[R, TaskrE, _]): ZIO[R, TaskrE, Unit] =
    ZIO.foreachPar_(as)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects in parallel, discarding the results.
    *
    * Unlike `foreachPar_`, this method will use at most up to `n` fibers.
    */
  final def foreachParN_[R, A, B](n: Long)(as: Iterable[A])(f: A => ZIO[R, TaskrE, _]): ZIO[R, TaskrE, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /** Evaluate each effect in the structure from left to right, and collect
    * the results. For a parallel version, see `collectAllPar`.
    */
  final def collectAll[R, A](in: Iterable[ZIO[R, TaskrE, A]]): ZIO[R, TaskrE, List[A]] =
    ZIO.collectAll(in)

  /** Evaluate each effect in the structure in parallel, and collect
    * the results. For a sequential version, see `collectAll`.
    */
  final def collectAllPar[R, A](as: Iterable[ZIO[R, TaskrE, A]]): ZIO[R, TaskrE, List[A]] =
    ZIO.collectAllPar(as)

  /** Evaluate each effect in the structure in parallel, and collect
    * the results. For a sequential version, see `collectAll`.
    *
    * Unlike `foreachAllPar`, this method will use at most `n` fibers.
    */
  final def collectAllParN[R, A](n: Long)(as: Iterable[ZIO[R, TaskrE, A]]): ZIO[R, TaskrE, List[A]] =
    ZIO.collectAllParN(n)(as)

  /** Races an `IO[E, A]` against zero or more other effects. Yields either the
    * first success or the last failure.
    */
  final def raceAll[R, R1 <: R, A](zio: ZIO[R, TaskrE, A],
      ios: Iterable[ZIO[R1, TaskrE, A]]): ZIO[R1, TaskrE, A] = ZIO.raceAll(zio, ios)

  /** Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
    */
  final def reduceAll[R, R1 <: R, A](a: ZIO[R, TaskrE, A], as: Iterable[ZIO[R1, TaskrE, A]])(f: (A, A) => A): ZIO[R1, TaskrE, A] =
    ZIO.reduceAll(a, as)(f)

  /** Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
    */
  final def reduceAllPar[R, R1 <: R, A](a: ZIO[R, TaskrE, A], as: Iterable[ZIO[R1, TaskrE, A]])(f: (A, A) => A): ZIO[R1, TaskrE, A] =
    ZIO.reduceAllPar(a, as)(f)

  /** Merges an `Iterable[IO]` to a single IO, working sequentially.
    */
  final def mergeAll[R, A, B](in: Iterable[ZIO[R, TaskrE, A]])(zero: B)(f: (B, A) => B): ZIO[R, TaskrE, B] =
    ZIO.mergeAll(in)(zero)(f)

  /** Merges an `Iterable[IO]` to a single IO, working in parallel.
    */
  final def mergeAllPar[R, A, B](in: Iterable[ZIO[R, TaskrE, A]])(zero: B)(f: (B, A) => B): ZIO[R, TaskrE, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /** Strictly-evaluated unit lifted into the `ZIO` monad.
    */
  final val unit: ZIO[Any, Nothing, Unit] = ZIO.unit

  /** The moral equivalent of `if (p) exp`
    */
  final def when[R](b: Boolean)(zio: ZIO[R, TaskrE, _]): ZIO[R, TaskrE, Unit] =
    ZIO.when(b)(zio)

  /** The moral equivalent of `if (p) exp` when `p` has side-effects
    */
  final def whenM[R](b: ZIO[R, TaskrE, Boolean])(zio: ZIO[R, TaskrE, _]): ZIO[R, TaskrE, Unit] =
    ZIO.whenM(b)(zio)

  /** Folds an `Iterable[A]` using an effectful function `f`, working sequentially.
    */
  final def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => ZIO[R, TaskrE, S]): ZIO[R, TaskrE, S] =
    ZIO.foldLeft(in)(zero)(f)

  /** Returns information about the current fiber, such as its identity.
    */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /** Constructs an effect based on information about the current fiber, such as
    * its identity.
    */
  final def descriptorWith[R, A](f: Fiber.Descriptor => ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.descriptorWith(f)

  /** Checks the interrupt status, and produces the effect returned by the
    * specified callback.
    */
  final def checkInterruptible[R, A](f: Boolean => ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
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
  final def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.uninterruptibleMask(k)

  /** Makes the effect interruptible, but passes it a restore function that
    * can be used to restore the inherited interruptibility from whatever region
    * the effect is composed into.
    */
  final def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.interruptibleMask(k)

  /** Prefix form of `ZIO#uninterruptible`.
    */
  final def uninterruptible[R, A](zio: ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
    ZIO.uninterruptible(zio)

  /** Prefix form of `ZIO#interruptible`.
    */
  final def interruptible[R, A](zio: ZIO[R, TaskrE, A]): ZIO[R, TaskrE, A] =
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
  def reserve[R, A, B](reservation: ZIO[R, TaskrE, Reservation[R, TaskrE, A]])(use: A => ZIO[R, TaskrE, B]): ZIO[R, TaskrE, B] =
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