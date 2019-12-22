package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Executor, Platform }

import scala.concurrent.ExecutionContext

object RIO {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  final def absolve[R, A](v: RIO[R, Either[Throwable, A]]): RIO[R, A] =
    ZIO.absolve(v)

  /**
   * @see See [[zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see See [[zio.ZIO.apply]]
   */
  final def apply[A](a: => A): Task[A] = ZIO.apply(a)

  /**
   * @see See [[zio.ZIO.access]]
   */
  final def access[R]: ZIO.AccessPartiallyApplied[R] =
    ZIO.access

  /**
   * @see See [[zio.ZIO.accessM]]
   */
  final def accessM[R]: ZIO.AccessMPartiallyApplied[R] =
    ZIO.accessM

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[R, A](acquire: RIO[R, A]): ZIO.BracketAcquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[R, A, B](
    acquire: RIO[R, A],
    release: A => ZIO[R, Nothing, Any],
    use: A => RIO[R, B]
  ): RIO[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[R, A](acquire: RIO[R, A]): ZIO.BracketExitAcquire[R, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[R, A, B](
    acquire: RIO[R, A],
    release: (A, Exit[Throwable, B]) => ZIO[R, Nothing, Any],
    use: A => RIO[R, B]
  ): RIO[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkDaemon]]
   */
  final def checkDaemon[R, A](f: DaemonStatus => RIO[R, A]): RIO[R, A] =
    ZIO.checkDaemon(f)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[R, A](f: InterruptStatus => RIO[R, A]): RIO[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[R, A](f: TracingStatus => RIO[R, A]): RIO[R, A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  final def children: UIO[Iterable[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  final def collectAll[R, A](in: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[R, A](as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[R, A](n: Int)(as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  final def collectAllSuccesses[R, A](in: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  final def collectAllSuccessesPar[R, A](as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  final def collectAllSuccessesParN[E, A](n: Int)(as: Iterable[RIO[E, A]]): RIO[E, List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  final def collectAllWith[R, A, B](in: Iterable[RIO[R, A]])(f: PartialFunction[A, B]): RIO[R, List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  final def collectAllWithPar[R, A, B](as: Iterable[RIO[R, A]])(f: PartialFunction[A, B]): RIO[R, List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  final def collectAllWithParN[R, A, B](n: Int)(as: Iterable[RIO[R, A]])(f: PartialFunction[A, B]): RIO[R, List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.daemonMask]]
   */
  final def daemonMask[R, A](k: ZIO.DaemonStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.daemonMask(k)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[R, A](f: Fiber.Descriptor => RIO[R, A]): RIO[R, A] =
    ZIO.descriptorWith(f)

  /**
   * @see See [[zio.ZIO.die]]
   */
  final def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * @see See [[zio.ZIO.dieMessage]]
   */
  final def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * @see See [[zio.ZIO.done]]
   */
  final def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[R, A](register: (RIO[R, A] => Unit) => Unit, blockingOn: List[Fiber.Id] = Nil): RIO[R, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[R, A](
    register: (RIO[R, A] => Unit) => Option[RIO[R, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): RIO[R, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[R, A](register: (RIO[R, A] => Unit) => RIO[R, Any]): RIO[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[R, A](
    register: (RIO[R, A] => Unit) => Either[Canceler[R], RIO[R, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): RIO[R, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * Returns a lazily constructed effect, whose construction may itself require effects.
   * When no environment is required (i.e., when R == Any) it is conceptually equivalent to `flatten(effect(io))`.
   */
  final def effectSuspend[R, A](rio: => RIO[R, A]): RIO[R, A] = ZIO.effectSuspend(rio)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  final def effectSuspendTotal[R, A](rio: => RIO[R, A]): RIO[R, A] = ZIO.effectSuspendTotal(rio)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  final def effectSuspendTotalWith[R, A](p: (Platform, Fiber.Id) => RIO[R, A]): RIO[R, A] =
    ZIO.effectSuspendTotalWith(p)

  /**
   * Returns a lazily constructed effect, whose construction may itself require effects.
   * When no environment is required (i.e., when R == Any) it is conceptually equivalent to `flatten(effect(io))`.
   */
  final def effectSuspendWith[R, A](p: (Platform, Fiber.Id) => RIO[R, A]): RIO[R, A] = ZIO.effectSuspendWith(p)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.environment]]
   */
  final def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * @see See [[zio.ZIO.fail]]
   */
  final def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  final val fiberId: UIO[Fiber.Id] = ZIO.fiberId

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  final def firstSuccessOf[R, A](
    rio: RIO[R, A],
    rest: Iterable[RIO[R, A]]
  ): RIO[R, A] = ZIO.firstSuccessOf(rio, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  final def flatten[R, A](taskr: RIO[R, RIO[R, A]]): RIO[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => RIO[R, S]): RIO[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foldRight]]
   */
  final def foldRight[R, S, A](in: Iterable[A])(zero: S)(f: (A, S) => RIO[R, S]): RIO[R, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  final def foreach[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[R, A, B](as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[R, A, B](n: Int)(as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  final def foreach_[R, A](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[R, A, B](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[R, A, B](n: Int)(as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  final def forkAll[R, A](as: Iterable[RIO[R, A]]): ZIO[R, Nothing, Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[R, A](as: Iterable[RIO[R, A]]): ZIO[R, Nothing, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Throwable, A]): Task[A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Throwable, A]): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: Task[Fiber[Throwable, A]]): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see See [[zio.ZIO.fromFunction]]
   */
  final def fromFunction[R, A](f: R => A): URIO[R, A] =
    ZIO.fromFunction(f)

  /**
   * @see See [[zio.ZIO.fromFunctionFuture]]
   */
  final def fromFunctionFuture[R, A](f: R => scala.concurrent.Future[A]): RIO[R, A] =
    ZIO.fromFunctionFuture(f)

  /**
   * @see See [[zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[R, A](f: R => Task[A]): RIO[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see See [[zio.ZIO.fromFuture]]
   */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * @see See [[zio.ZIO.fromFutureInterrupt]]
   */
  final def fromFutureInterrupt[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFutureInterrupt(make)

  final def fromOption[A](v: => Option[A]): IO[Unit, A] = ZIO.fromOption(v)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Throwable]): Task[Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  final def haltWith[R](function: (() => ZTrace) => Cause[Throwable]): RIO[R, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see See [[zio.ZIO.identity]]
   */
  final def identity[R]: RIO[Nothing, R] = ZIO.identity

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  final def interruptAs(fiberId: Fiber.Id): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  final def interruptible[R, A](taskr: RIO[R, A]): RIO[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  final def lock[R, A](executor: Executor)(taskr: RIO[R, A]): RIO[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  final def left[R, A](a: A): RIO[R, Either[A, Nothing]] = ZIO.left(a)

  /**
   *  @see mapN in [[zio.ZIO$ ZIO]]
   */
  final def mapN[R, A, B, C](rio1: RIO[R, A], rio2: RIO[R, B])(f: (A, B) => C): RIO[R, C] =
    ZIO.mapN(rio1, rio2)(f)

  /**
   *  @see mapN in [[zio.ZIO$ ZIO]]
   */
  final def mapN[R, A, B, C, D](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C])(
    f: (A, B, C) => D
  ): RIO[R, D] =
    ZIO.mapN(rio1, rio2, rio3)(f)

  /**
   *  @see mapN in [[zio.ZIO$ ZIO]]
   */
  final def mapN[R, A, B, C, D, F](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C], rio4: RIO[R, D])(
    f: (A, B, C, D) => F
  ): RIO[R, F] =
    ZIO.mapN(rio1, rio2, rio3, rio4)(f)

  /**
   *  @see mapParN in [[zio.ZIO$ ZIO]]
   */
  final def mapParN[R, A, B, C](rio1: RIO[R, A], rio2: RIO[R, B])(f: (A, B) => C): RIO[R, C] =
    ZIO.mapParN(rio1, rio2)(f)

  /**
   *  @see mapParN in [[zio.ZIO$ ZIO]]
   */
  final def mapParN[R, A, B, C, D](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C])(f: (A, B, C) => D): RIO[R, D] =
    ZIO.mapParN(rio1, rio2, rio3)(f)

  /**
   *  @see mapParN in [[zio.ZIO$ ZIO]]
   */
  final def mapParN[R, A, B, C, D, F](rio1: RIO[R, A], rio2: RIO[R, B], rio3: RIO[R, C], rio4: RIO[R, D])(
    f: (A, B, C, D) => F
  ): RIO[R, F] =
    ZIO.mapParN(rio1, rio2, rio3, rio4)(f)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[R, A, B](in: Iterable[RIO[R, A]])(zero: B)(f: (B, A) => B): RIO[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[R, A, B](in: Iterable[RIO[R, A]])(zero: B)(f: (B, A) => B): RIO[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.nonDaemonMask]]
   */
  final def nonDaemonMask[R, A](k: ZIO.DaemonStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.nonDaemonMask(k)

  /**
   * @see See [[zio.ZIO.none]]
   */
  final val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.partitionM]]
   */
  final def partitionM[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[Nothing, (List[Throwable], List[B])] =
    ZIO.partitionM(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMPar]]
   */
  final def partitionMPar[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[Nothing, (List[Throwable], List[B])] =
    ZIO.partitionMPar(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMParN]]
   */
  final def partitionMParN[R, A, B](n: Int)(
    in: Iterable[A]
  )(f: A => RIO[R, B]): RIO[Nothing, (List[Throwable], List[B])] =
    ZIO.partitionMParN(n)(in)(f)

  /**
   * @see See [[zio.ZIO.provide]]
   */
  final def provide[R, A](r: R): RIO[R, A] => Task[A] =
    ZIO.provide(r)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  final def raceAll[R, R1 <: R, A](taskr: RIO[R, A], taskrs: Iterable[RIO[R1, A]]): RIO[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[R, R1 <: R, A](a: RIO[R, A], as: Iterable[RIO[R1, A]])(f: (A, A) => A): RIO[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[R, R1 <: R, A](a: RIO[R, A], as: Iterable[RIO[R1, A]])(f: (A, A) => A): RIO[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[R, A](n: Int)(effect: RIO[R, A]): Iterable[RIO[R, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.require]]
   */
  final def require[A](error: Throwable): IO[Throwable, Option[A]] => IO[Throwable, A] =
    ZIO.require[Any, Throwable, A](error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  final def reserve[R, A, B](reservation: RIO[R, Reservation[R, Throwable, A]])(use: A => RIO[R, B]): RIO[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[R, B](b: B): RIO[R, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  final def runtime[R]: ZIO[R, Nothing, Runtime[R]] = ZIO.runtime

  /**
   * @see See [[zio.ZIO.sleep]]
   */
  final def sleep(duration: Duration): RIO[Clock, Unit] =
    ZIO.sleep(duration)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[R, A](a: A): RIO[R, Option[A]] = ZIO.some(a)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   *  See [[zio.ZIO.sequence]]
   */
  final def sequence[R, A](in: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.sequence(in)

  /**
   *  See [[zio.ZIO.sequencePar]]
   */
  final def sequencePar[R, A](as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  final def sequenceParN[R, A](n: Int)(as: Iterable[RIO[R, A]]): RIO[R, List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   * @see See [[zio.ZIO.swap]]
   */
  final def swap[R, A, B](implicit ev: R <:< (A, B)): RIO[R, (B, A)] =
    ZIO.swap

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  final def traced[R, A](zio: RIO[R, A]): RIO[R, A] = ZIO.traced(zio)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  final def traverse[R, A, B](in: Iterable[A])(f: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  final def traversePar[R, A, B](as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  final def traverseParN[R, A, B](
    n: Int
  )(as: Iterable[A])(fn: A => RIO[R, B]): RIO[R, List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverse_]]
   */
  final def traverse_[R, A](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[R, A](as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[R, A](
    n: Int
  )(as: Iterable[A])(f: A => RIO[R, Any]): RIO[R, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[R, A](taskr: RIO[R, A]): RIO[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => RIO[R, A]): RIO[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unsandbox]]
   */
  final def unsandbox[R, A](v: IO[Cause[Throwable], A]): RIO[R, A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  final def untraced[R, A](zio: RIO[R, A]): RIO[R, A] = ZIO.untraced(zio)

  /**
   * @see See [[zio.ZIO.when]]
   */
  final def when[R](b: Boolean)(rio: RIO[R, Any]): RIO[R, Unit] =
    ZIO.when(b)(rio)

  /**
   * @see See [[zio.ZIO.whenCase]]
   */
  final def whenCase[R, E, A](a: A)(pf: PartialFunction[A, ZIO[R, E, Any]]): ZIO[R, E, Unit] =
    ZIO.whenCase(a)(pf)

  /**
   * @see See [[zio.ZIO.whenCaseM]]
   */
  final def whenCaseM[R, E, A](a: ZIO[R, E, A])(pf: PartialFunction[A, ZIO[R, E, Any]]): ZIO[R, E, Unit] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see See [[zio.ZIO.whenM]]
   */
  final def whenM[R](b: RIO[R, Boolean])(rio: RIO[R, Any]): RIO[R, Unit] =
    ZIO.whenM(b)(rio)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * @see See [[zio.ZIO._1]]
   */
  final def _1[R, A, B](implicit ev: R <:< (A, B)): RIO[R, A] = ZIO._1

  /**
   * @see See [[zio.ZIO._2]]
   */
  final def _2[R, A, B](implicit ev: R <:< (A, B)): RIO[R, B] = ZIO._2

}
