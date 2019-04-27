package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.internal.{ Executor, Platform }


/*
 * PHASE 1 DONE
 * PHASE 2 DONE
 * PHASE 3 DONE
 * PHASE 4 DONE
 */

/*
 * EXTENDS ZIOFunctions
 */
object UIO {

  type UioE = Nothing
  type UioR = Any

  def apply[A](a: => A): UIO[A] = effectTotal(a)

  /*
   * ---------------------------------------------------------------------------------------
   * The Following is taken from ZIOFunctions.
   * ---------------------------------------------------------------------------------------
   */

  /** Returns an effect that models failure with the specified error.
    * The moral equivalent of `throw` for pure code.
    */
  final def fail(error: UioE): IO[UioE, Nothing] = ZIO.fail(error)

  /** Returns an effect that models failure with the specified `Cause`.
    */
  final def halt(cause: Cause[UioE]): IO[UioE, Nothing] = ZIO.halt(cause)

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
  final def environment: ZIO[UioR, Nothing, UioR] = ZIO.access(ZIO.identityFn[UioR])

  /** Accesses the environment of the effect.
    * {{{
    * val portNumber = effect.access(_.config.portNumber)
    * }}}
    */
  final def access: ZIO.AccessPartiallyApplied[UioR] =
    ZIO.access

  /** Effectfully accesses the environment of the effect.
    */
  final def accessM: ZIO.AccessMPartiallyApplied[UioR] =
    ZIO.accessM

  /** Given an environment `R`, returns a function that can supply the
    * environment to programs that require it, removing their need for any
    * specific environment.
    *
    * This is similar to dependency injection, and the `provide` function can be
    * thought of as `inject`.
    */
  final def provide[A](r: UioR): ZIO[UioR, UioE, A] => IO[UioE, A] =
    ZIO.provide(r)

  /** Returns an effect that accesses the runtime, which can be used to
    * (unsafely) execute tasks. This is useful for integration with
    * non-functional code that must call back into functional code.
    */
  final def runtime: ZIO[UioR, Nothing, Runtime[UioR]] = ZIO.runtime

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
  final def forkAll[A](as: Iterable[ZIO[UioR, UioE, A]]): ZIO[UioR, Nothing, Fiber[UioE, List[A]]] =
    ZIO.forkAll(as)

  /** Returns an effect that forks all of the specified values, and returns a
    * composite fiber that produces unit. This version is faster than [[forkAll]]
    * in cases where the results of the forked fibers are not needed.
    */
  final def forkAll_[A](as: Iterable[ZIO[UioR, UioE, A]]): ZIO[UioR, Nothing, Unit] =
    ZIO.forkAll_(as)

  /** Returns an effect from a [[scalaz.zio.Exit]] value.
    */
  final def done[A](r: Exit[UioE, A]): IO[UioE, A] = ZIO.done(r)

  /** Enables supervision for this effect. This will cause fibers forked by
    * this effect to be tracked and will enable their inspection via [[ZIO.children]].
    */
  final def supervised[A](zio: ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.supervised(zio)

  /** Returns an effect that supervises the specified effect, ensuring that all
    * fibers that it forks are interrupted as soon as the supervised effect
    * completes.
    */
  final def supervise[A](zio: ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.supervise(zio)

  /** Returns an effect that supervises the specified effect, ensuring that all
    * fibers that it forks are passed to the specified supervisor as soon as the
    * supervised effect completes.
    */
  final def superviseWith[A](zio: ZIO[UioR, UioE, A])(supervisor: IndexedSeq[Fiber[_, _]] => ZIO[UioR, Nothing, _]): ZIO[UioR, UioE, A] =
    ZIO.superviseWith(zio)(supervisor)

  /** Returns an effect that first executes the outer effect, and then executes
    * the inner effect, returning the value from the inner effect, and effectively
    * flattening a nested effect.
    */
  final def flatten[A](zio: ZIO[UioR, UioE, ZIO[UioR, UioE, A]]): ZIO[UioR, UioE, A] =
    ZIO.flatten(zio)

  /** Returns a lazily constructed effect, whose construction may itself require
    * effects. This is a shortcut for `flatten(effectTotal(io))`.
    */
  final def suspend[A](io: => ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.suspend(io)

  /** Returns an effect that will execute the specified effect fully on the
    * provided executor, before returning to the default executor.
    */
  final def lock[A](executor: Executor)(zio: ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.lock(executor)(zio)

  /** Imports an asynchronous effect into a pure `ZIO` value. See `effectAsyncMaybe` for
    * the more expressive variant of this function that can return a value
    * synchronously.
    */
  final def effectAsync[A](register: (ZIO[Any, UioE, A] => Unit) => Unit): ZIO[Any, UioE, A] =
    ZIO.effectAsync(register)

  /** Imports an asynchronous effect into a pure `ZIO` value, possibly returning
    * the value synchronously.
    */
  final def effectAsyncMaybe[A](register: (ZIO[Any, UioE, A] => Unit) => Option[IO[UioE, A]]): ZIO[Any, UioE, A] =
    ZIO.effectAsyncMaybe(register)

  /** Imports an asynchronous effect into a pure `ZIO` value. This formulation is
    * necessary when the effect is itself expressed in terms of `ZIO`.
    */
  final def effectAsyncM[A](register: (ZIO[UioR, UioE, A] => Unit) => ZIO[UioR, Nothing, _]): ZIO[UioR, UioE, A] =
    ZIO.effectAsyncM(register)

  /** Imports an asynchronous effect into a pure `IO` value. The effect has the
    * option of returning the value synchronously, which is useful in cases
    * where it cannot be determined if the effect is synchronous or asynchronous
    * until the effect is actually executed. The effect also has the option of
    * returning a canceler, which will be used by the runtime to cancel the
    * asynchronous effect if the fiber executing the effect is interrupted.
    */
  final def effectAsyncInterrupt[A](register: (ZIO[UioR, UioE, A] => Unit) => Either[Canceler, ZIO[UioR, UioE, A]]): ZIO[UioR, UioE, A] =
    ZIO.effectAsyncInterrupt(register)

  /** Submerges the error case of an `Either` into the `ZIO`. The inverse
    * operation of `IO.either`.
    */
  final def absolve[A](v: ZIO[UioR, UioE, Either[UioE, A]]): ZIO[UioR, UioE, A] =
    ZIO.absolve(v)

  /** The inverse operation `IO.sandboxed`
    *
    * Terminates with exceptions on the `Left` side of the `Either` error, if it
    * exists. Otherwise extracts the contained `IO[E, A]`
    */
  final def unsandbox[A](v: ZIO[UioR, Cause[UioE], A]): ZIO[UioR, UioE, A] = ZIO.unsandbox(v)

  /** Returns the identity effectful function, which performs no effects
    */
  final def identity: ZIO[UioR, Nothing, UioR] = ZIO.identity

  /** Returns an effectful function that merely swaps the elements in a `Tuple2`.
    *
    *  Q: Leave Implicit?
    */
  final def swap[A, B](implicit ev: UioR <:< (A, B)): ZIO[UioR, UioE, (B, A)] =
    ZIO.swap

  /** Returns an effectful function that extracts out the first element of a
    * tuple.
    */
  final def _1[A, B](implicit ev: UioR <:< (A, B)): ZIO[UioR, UioE, A] = ZIO._1

  /** Returns an effectful function that extracts out the second element of a
    * tuple.
    */
  final def _2[A, B](implicit ev: UioR <:< (A, B)): ZIO[UioR, UioE, B] = ZIO._2

  /** Lifts a function `R => A` into a `ZIO[R, Nothing, A]`.
    */
  final def fromFunction[A](f: UioR => A): ZIO[UioR, Nothing, A] =
    ZIO.fromFunction(f)

  /** Lifts an effectful function whose effect requires no environment into
    * an effect that requires the input to the function.
    */
  final def fromFunctionM[A](f: UioR => IO[UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.fromFunctionM(f)

  /** Lifts an `Either` into a `ZIO` value.
    */
  final def fromEither[A](v: => Either[UioE, A]): IO[UioE, A] =
    ZIO.fromEither(v)

  /** Creates a `ZIO` value that represents the exit value of the specified
    * fiber.
    */
  final def fromFiber[A](fiber: => Fiber[UioE, A]): IO[UioE, A] =
    ZIO.fromFiber(fiber)

  /** Creates a `ZIO` value that represents the exit value of the specified
    * fiber.
    */
  final def fromFiberM[A](fiber: IO[UioE, Fiber[UioE, A]]): IO[UioE, A] =
    ZIO.fromFiberM(fiber)

  /** Requires that the given `IO[E, Option[A]]` contain a value. If there is no
    * value, then the specified error will be raised.
    */
  final def require[A](error: UioE): IO[UioE, Option[A]] => IO[UioE, A] =
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
  final def bracket[A](acquire: ZIO[UioR, UioE, A]): ZIO.BracketAcquire[UioR, UioE, A] =
    ZIO.bracket(acquire)

  /** Uncurried version. Doesn't offer curried syntax and have worse type-inference
    * characteristics, but guarantees no extra allocations of intermediate
    * [[scalaz.zio.ZIO.BracketAcquire]] and [[scalaz.zio.ZIO.BracketRelease]] objects.
    */
  final def bracket[A, B](acquire: ZIO[UioR, UioE, A],
      release: A => ZIO[UioR, Nothing, _],
      use: A => ZIO[UioR, UioE, B]): ZIO[UioR, UioE, B] = ZIO.bracket(acquire, release, use)

  /** Acquires a resource, uses the resource, and then releases the resource.
    * Neither the acquisition nor the release will be interrupted, and the
    * resource is guaranteed to be released, so long as the `acquire` effect
    * succeeds. If `use` fails, then after release, the returned effect will fail
    * with the same error.
    */
  final def bracketExit[A](acquire: ZIO[UioR, UioE, A]): ZIO.BracketExitAcquire[UioR, UioE, A] =
    ZIO.bracketExit(acquire)

  /** Uncurried version. Doesn't offer curried syntax and have worse type-inference
    * characteristics, but guarantees no extra allocations of intermediate
    * [[scalaz.zio.ZIO.BracketExitAcquire]] and [[scalaz.zio.ZIO.BracketExitRelease]] objects.
    */
  final def bracketExit[A, B](acquire: ZIO[UioR, UioE, A],
      release: (A, Exit[UioE, B]) => ZIO[UioR, Nothing, _],
      use: A => ZIO[UioR, UioE, B]): ZIO[UioR, UioE, B] = ZIO.bracketExit(acquire, release, use)

  /** Applies the function `f` to each element of the `Iterable[A]` and
    * returns the results in a new `List[B]`.
    *
    * For a parallel version of this method, see `foreachPar`.
    */
  final def foreach[A, B](in: Iterable[A])(f: A => ZIO[UioR, UioE, B]): ZIO[UioR, UioE, List[B]] =
    ZIO.foreach(in)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` in parallel,
    * and returns the results in a new `List[B]`.
    *
    * For a sequential version of this method, see `foreach`.
    */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => ZIO[UioR, UioE, B]): ZIO[UioR, UioE, List[B]] =
    ZIO.foreachPar(as)(fn)

  /** Applies the function `f` to each element of the `Iterable[A]` in parallel,
    * and returns the results in a new `List[B]`.
    *
    * Unlike `foreachPar`, this method will use at most up to `n` fibers.
    */
  final def foreachParN[A, B](n: Long)(as: Iterable[A])(fn: A => ZIO[UioR, UioE, B]): ZIO[UioR, UioE, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects sequentially.
    *
    * Equivalent to `foreach(as)(f).void`, but without the cost of building
    * the list of results.
    */
  final def foreach_[A](as: Iterable[A])(f: A => ZIO[UioR, UioE, _]): ZIO[UioR, UioE, Unit] =
    ZIO.foreach_(as)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects in parallel, discarding the results.
    *
    * For a sequential version of this method, see `foreach_`.
    */
  final def foreachPar_[A, B](as: Iterable[A])(f: A => ZIO[UioR, UioE, _]): ZIO[UioR, UioE, Unit] =
    ZIO.foreachPar_(as)(f)

  /** Applies the function `f` to each element of the `Iterable[A]` and runs
    * produced effects in parallel, discarding the results.
    *
    * Unlike `foreachPar_`, this method will use at most up to `n` fibers.
    */
  final def foreachParN_[A, B](n: Long)(as: Iterable[A])(f: A => ZIO[UioR, UioE, _]): ZIO[UioR, UioE, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /** Evaluate each effect in the structure from left to right, and collect
    * the results. For a parallel version, see `collectAllPar`.
    */
  final def collectAll[A](in: Iterable[ZIO[UioR, UioE, A]]): ZIO[UioR, UioE, List[A]] =
    ZIO.collectAll(in)

  /** Evaluate each effect in the structure in parallel, and collect
    * the results. For a sequential version, see `collectAll`.
    */
  final def collectAllPar[A](as: Iterable[ZIO[UioR, UioE, A]]): ZIO[UioR, UioE, List[A]] =
    ZIO.collectAllPar(as)

  /** Evaluate each effect in the structure in parallel, and collect
    * the results. For a sequential version, see `collectAll`.
    *
    * Unlike `foreachAllPar`, this method will use at most `n` fibers.
    */
  final def collectAllParN[A](n: Long)(as: Iterable[ZIO[UioR, UioE, A]]): ZIO[UioR, UioE, List[A]] =
    ZIO.collectAllParN(n)(as)

  /** Races an `IO[E, A]` against zero or more other effects. Yields either the
    * first success or the last failure.
    */
  final def raceAll[R1 <: UioR, A](zio: ZIO[UioR, UioE, A],
      ios: Iterable[ZIO[R1, UioE, A]]): ZIO[R1, UioE, A] = ZIO.raceAll(zio, ios)

  /** Reduces an `Iterable[IO]` to a single `IO`, working sequentially.
    */
  final def reduceAll[R1 <: UioR, A](a: ZIO[UioR, UioE, A], as: Iterable[ZIO[R1, UioE, A]])(f: (A, A) => A): ZIO[R1, UioE, A] =
    ZIO.reduceAll(a, as)(f)

  /** Reduces an `Iterable[IO]` to a single `IO`, working in parallel.
    */
  final def reduceAllPar[R1 <: UioR, A](a: ZIO[UioR, UioE, A], as: Iterable[ZIO[R1, UioE, A]])(f: (A, A) => A): ZIO[R1, UioE, A] =
    ZIO.reduceAllPar(a, as)(f)

  /** Merges an `Iterable[IO]` to a single IO, working sequentially.
    */
  final def mergeAll[A, B](in: Iterable[ZIO[UioR, UioE, A]])(zero: B)(f: (B, A) => B): ZIO[UioR, UioE, B] =
    ZIO.mergeAll(in)(zero)(f)

  /** Merges an `Iterable[IO]` to a single IO, working in parallel.
    */
  final def mergeAllPar[A, B](in: Iterable[ZIO[UioR, UioE, A]])(zero: B)(f: (B, A) => B): ZIO[UioR, UioE, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /** Strictly-evaluated unit lifted into the `ZIO` monad.
    */
  final val unit: ZIO[Any, Nothing, Unit] = ZIO.unit

  /** The moral equivalent of `if (p) exp`
    */
  final def when(b: Boolean)(zio: ZIO[UioR, UioE, _]): ZIO[UioR, UioE, Unit] =
    ZIO.when(b)(zio)

  /** The moral equivalent of `if (p) exp` when `p` has side-effects
    */
  final def whenM(b: ZIO[UioR, UioE, Boolean])(zio: ZIO[UioR, UioE, _]): ZIO[UioR, UioE, Unit] =
    ZIO.whenM(b)(zio)

  /** Folds an `Iterable[A]` using an effectful function `f`, working sequentially.
    */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => ZIO[UioR, UioE, S]): ZIO[UioR, UioE, S] =
    ZIO.foldLeft(in)(zero)(f)

  /** Returns information about the current fiber, such as its identity.
    */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /** Constructs an effect based on information about the current fiber, such as
    * its identity.
    */
  final def descriptorWith[A](f: Fiber.Descriptor => ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.descriptorWith(f)

  /** Checks the interrupt status, and produces the effect returned by the
    * specified callback.
    */
  final def checkInterruptible[A](f: Boolean => ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
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
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.uninterruptibleMask(k)

  /** Makes the effect interruptible, but passes it a restore function that
    * can be used to restore the inherited interruptibility from whatever region
    * the effect is composed into.
    */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.interruptibleMask(k)

  /** Prefix form of `ZIO#uninterruptible`.
    */
  final def uninterruptible[A](zio: ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
    ZIO.uninterruptible(zio)

  /** Prefix form of `ZIO#interruptible`.
    */
  final def interruptible[A](zio: ZIO[UioR, UioE, A]): ZIO[UioR, UioE, A] =
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
  def reserve[A, B](reservation: ZIO[UioR, UioE, Reservation[UioR, UioE, A]])(use: A => ZIO[UioR, UioE, B]): ZIO[UioR, UioE, B] =
    ZIO.reserve(reservation)(use)
}