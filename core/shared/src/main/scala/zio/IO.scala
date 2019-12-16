package zio

import zio.internal.{ Executor, Platform }

import scala.concurrent.ExecutionContext

object IO {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  final def absolve[E, A](v: IO[E, Either[E, A]]): IO[E, A] =
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
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[E, A](acquire: IO[E, A]): BracketAcquire[E, A] =
    new BracketAcquire(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[E, A, B](acquire: IO[E, A], release: A => UIO[Any], use: A => IO[E, B]): IO[E, B] =
    ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[E, A](acquire: IO[E, A]): ZIO.BracketExitAcquire[Any, E, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[E, A, B](
    acquire: IO[E, A],
    release: (A, Exit[E, B]) => UIO[Any],
    use: A => IO[E, B]
  ): IO[E, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See bracketFork [[zio.ZIO]]
   */
  final def bracketFork[E, A](acquire: IO[E, A]): BracketForkAcquire[E, A] =
    new BracketForkAcquire(acquire)

  /**
   * @see See bracketFork [[zio.ZIO]]
   */
  final def bracketFork[E, A, B](acquire: IO[E, A], release: A => UIO[Any], use: A => IO[E, B]): IO[E, B] =
    ZIO.bracketFork(acquire, release, use)

  /**
   * @see See bracketForkExit [[zio.ZIO]]
   */
  final def bracketForkExit[E, A](acquire: IO[E, A]): ZIO.BracketForkExitAcquire[Any, E, A] =
    ZIO.bracketForkExit(acquire)

  /**
   * @see See bracketForkExit [[zio.ZIO]]
   */
  final def bracketForkExit[E, A, B](
    acquire: IO[E, A],
    release: (A, Exit[E, B]) => UIO[Any],
    use: A => IO[E, B]
  ): IO[E, B] =
    ZIO.bracketForkExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkDaemon]]
   */
  final def checkDaemon[E, A](f: DaemonStatus => IO[E, A]): IO[E, A] =
    ZIO.checkDaemon(f)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[E, A](f: InterruptStatus => IO[E, A]): IO[E, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[E, A](f: TracingStatus => IO[E, A]): IO[E, A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  final def children: UIO[Iterable[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  final def collectAll[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[E, A](n: Int)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  final def collectAllSuccesses[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  final def collectAllSuccessesPar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  final def collectAllSuccessesParN[E, A](n: Int)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  final def collectAllWith[E, A, B](in: Iterable[IO[E, A]])(f: PartialFunction[A, B]): IO[E, List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  final def collectAllWithPar[E, A, B](as: Iterable[IO[E, A]])(f: PartialFunction[A, B]): IO[E, List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  final def collectAllWithParN[E, A, B](n: Int)(as: Iterable[IO[E, A]])(f: PartialFunction[A, B]): IO[E, List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.daemonMask]]
   */
  final def daemonMask[E, A](k: ZIO.DaemonStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.daemonMask(k)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[E, A](f: Fiber.Descriptor => IO[E, A]): IO[E, A] =
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
  final def done[E, A](r: Exit[E, A]): IO[E, A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[E, A](register: (IO[E, A] => Unit) => Unit, blockingOn: List[Fiber.Id] = Nil): IO[E, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[E, A](
    register: (IO[E, A] => Unit) => Either[Canceler[Any], IO[E, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): IO[E, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[E, A](register: (IO[E, A] => Unit) => IO[E, Any]): IO[E, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[E, A](
    register: (IO[E, A] => Unit) => Option[IO[E, A]],
    blockingOn: List[Fiber.Id] = Nil
  ): IO[E, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see [[zio.ZIO.effectSuspend]]
   */
  final def effectSuspend[A](io: => IO[Throwable, A]): IO[Throwable, A] = ZIO.effectSuspend(io)

  /**
   * @see [[zio.ZIO.effectSuspendWith]]
   */
  final def effectSuspendWith[A](p: (Platform, Fiber.Id) => IO[Throwable, A]): IO[Throwable, A] =
    ZIO.effectSuspendWith(p)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  final def effectSuspendTotal[E, A](io: => IO[E, A]): IO[E, A] = ZIO.effectSuspendTotal(io)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  final def effectSuspendTotalWith[E, A](p: (Platform, Fiber.Id) => IO[E, A]): IO[E, A] = ZIO.effectSuspendTotalWith(p)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.fail]]
   */
  final def fail[E](error: E): IO[E, Nothing] = ZIO.fail(error)

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  final val fiberId: UIO[Fiber.Id] = ZIO.fiberId

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  final def firstSuccessOf[E, A](
    io: IO[E, A],
    rest: Iterable[IO[E, A]]
  ): IO[E, A] = ZIO.firstSuccessOf(io, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  final def flatten[E, A](io: IO[E, IO[E, A]]): IO[E, A] =
    ZIO.flatten(io)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[E, S, A](in: Iterable[A])(zero: S)(f: (S, A) => IO[E, S]): IO[E, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foldRight]]
   */
  final def foldRight[E, S, A](in: Iterable[A])(zero: S)(f: (A, S) => IO[E, S]): IO[E, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  final def foreach[E, A, B](in: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[E, A, B](n: Int)(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  final def foreach_[E, A](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[E, A, B](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[E, A, B](n: Int)(as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  final def forkAll[E, A](as: Iterable[IO[E, A]]): UIO[Fiber[E, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[E, A](as: Iterable[IO[E, A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  final def fromEither[E, A](v: => Either[E, A]): IO[E, A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[E, A](fiber: => Fiber[E, A]): IO[E, A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[E, A](fiber: IO[E, Fiber[E, A]]): IO[E, A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see [[zio.ZIO.fromFunction]]
   */
  final def fromFunction[A](f: Any => A): IO[Nothing, A] = ZIO.fromFunction(f)

  /**
   * @see [[zio.ZIO.fromFunctionFuture]]
   */
  final def fromFunctionFuture[A](f: Any => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFunctionFuture(f)

  /**
   * @see [[zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[E, A](f: Any => IO[E, A]): IO[E, A] = ZIO.fromFunctionM(f)

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

  /**
   * @see See [[zio.ZIO.fromOption]]
   */
  final def fromOption[A](v: => Option[A]): IO[Unit, A] = ZIO.fromOption(v)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  final def halt[E](cause: Cause[E]): IO[E, Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  final def haltWith[E](function: (() => ZTrace) => Cause[E]): IO[E, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.identity]]
   */
  final def identity: IO[Nothing, Any] = ZIO.identity

  /**
   * @see See See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  final def interruptAs(fiberId: Fiber.Id): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  final def interruptible[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.interruptible(io)

  /**
   * @see See [[zio.ZIO.interruptibleFork]]
   */
  final def interruptibleFork[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.interruptibleFork(io)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.interruptibleMask(k)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  final def left[E, A](a: A): IO[E, Either[A, Nothing]] = ZIO.left(a)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  final def lock[E, A](executor: Executor)(io: IO[E, A]): IO[E, A] =
    ZIO.lock(executor)(io)

  /**
   *  @see [[zio.ZIO.mapN]]
   */
  final def mapN[E, A, B, C](io1: IO[E, A], io2: IO[E, B])(f: (A, B) => C): IO[E, C] =
    ZIO.mapN(io1, io2)(f)

  /**
   *  @see [[zio.ZIO.mapN]]
   */
  final def mapN[E, A, B, C, D](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C])(f: (A, B, C) => D): IO[E, D] =
    ZIO.mapN(io1, io2, io3)(f)

  /**
   *  @see [[zio.ZIO.mapN]]
   */
  final def mapN[E, A, B, C, D, F](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C], io4: IO[E, D])(
    f: (A, B, C, D) => F
  ): IO[E, F] =
    ZIO.mapN(io1, io2, io3, io4)(f)

  /**
   *  @see [[zio.ZIO.mapParN]]
   */
  final def mapParN[E, A, B, C](io1: IO[E, A], io2: IO[E, B])(f: (A, B) => C): IO[E, C] =
    ZIO.mapParN(io1, io2)(f)

  /**
   *  @see [[zio.ZIO.mapParN]]
   */
  final def mapParN[E, A, B, C, D](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C])(f: (A, B, C) => D): IO[E, D] =
    ZIO.mapParN(io1, io2, io3)(f)

  /**
   *  @see [[zio.ZIO.mapParN]]
   */
  final def mapParN[E, A, B, C, D, F](io1: IO[E, A], io2: IO[E, B], io3: IO[E, C], io4: IO[E, D])(
    f: (A, B, C, D) => F
  ): IO[E, F] =
    ZIO.mapParN(io1, io2, io3, io4)(f)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.nonDaemonMask]]
   */
  final def nonDaemonMask[E, A](k: ZIO.DaemonStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.nonDaemonMask(k)

  /**
   * @see See [[zio.ZIO.none]]
   */
  final val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.partitionM]]
   */
  final def partitionM[E, A, B](
    in: Iterable[A]
  )(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[Nothing, (List[E], List[B])] =
    ZIO.partitionM(in)(f)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  final def raceAll[E, A](io: IO[E, A], ios: Iterable[IO[E, A]]): IO[E, A] = ZIO.raceAll(io, ios)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[E, A](n: Int)(effect: IO[E, A]): Iterable[IO[E, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.require]]
   */
  final def require[E, A](error: E): IO[E, Option[A]] => IO[E, A] =
    ZIO.require[Any, E, A](error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  final def reserve[E, A, B](reservation: IO[E, Reservation[Any, E, A]])(use: A => IO[E, B]): IO[E, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[E, B](b: B): IO[E, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  final def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   *  See [[zio.ZIO.sequence]]
   */
  final def sequence[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.sequence(in)

  /**
   *  See [[zio.ZIO.sequencePar]]
   */
  final def sequencePar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  final def sequenceParN[E, A](n: Int)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[E, A](a: A): IO[E, Option[A]] = ZIO.some(a)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  final def traced[E, A](zio: IO[E, A]): IO[E, A] = ZIO.traced(zio)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  final def traverse[E, A, B](in: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  final def traversePar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  final def traverseParN[E, A, B](
    n: Int
  )(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverse_]]
   */
  final def traverse_[E, A](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[E, A](as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[E, A](
    n: Int
  )(as: Iterable[A])(f: A => IO[E, Any]): IO[E, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.uninterruptible(io)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unsandbox]]
   */
  final def unsandbox[E, A](v: IO[Cause[E], A]): IO[E, A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  final def untraced[E, A](zio: IO[E, A]): IO[E, A] = ZIO.untraced(zio)

  /**
   * @see See [[zio.ZIO.validateM]]
   */
  final def validateM[E, A, B](in: Iterable[A])(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[List[E], List[B]] =
    ZIO.validateM(in)(f)

  /**
   * @see See [[zio.ZIO.validateFirstM]]
   */
  final def validateFirstM[E, A, B](in: Iterable[A])(f: A => IO[E, B])(implicit ev: CanFail[E]): IO[List[E], B] =
    ZIO.validateFirstM(in)(f)

  /**
   * @see See [[zio.ZIO.when]]
   */
  final def when[E](b: Boolean)(io: IO[E, Any]): IO[E, Unit] =
    ZIO.when(b)(io)

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
  final def whenM[E](b: IO[E, Boolean])(io: IO[E, Any]): IO[E, Unit] =
    ZIO.whenM(b)(io)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  final class BracketAcquire_[E](private val acquire: IO[E, Any]) extends AnyVal {
    def apply(release: IO[Nothing, Any]): BracketRelease_[E] =
      new BracketRelease_(acquire, release)
  }
  final class BracketRelease_[E](acquire: IO[E, Any], release: IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: IO[E1, B]): IO[E1, B] =
      ZIO.bracket(acquire, (_: Any) => release, (_: Any) => use)
  }

  final class BracketAcquire[E, A](private val acquire: IO[E, A]) extends AnyVal {
    def apply(release: A => IO[Nothing, Any]): BracketRelease[E, A] =
      new BracketRelease[E, A](acquire, release)
  }
  class BracketRelease[E, A](acquire: IO[E, A], release: A => IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: A => IO[E1, B]): IO[E1, B] =
      ZIO.bracket(acquire, release, use)
  }

  final class BracketForkAcquire_[E](private val acquire: IO[E, Any]) extends AnyVal {
    def apply(release: IO[Nothing, Any]): BracketForkRelease_[E] =
      new BracketForkRelease_(acquire, release)
  }
  final class BracketForkRelease_[E](acquire: IO[E, Any], release: IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: IO[E1, B]): IO[E1, B] =
      ZIO.bracketFork(acquire, (_: Any) => release, (_: Any) => use)
  }

  final class BracketForkAcquire[E, A](private val acquire: IO[E, A]) extends AnyVal {
    def apply(release: A => IO[Nothing, Any]): BracketForkRelease[E, A] =
      new BracketForkRelease[E, A](acquire, release)
  }
  class BracketForkRelease[E, A](acquire: IO[E, A], release: A => IO[Nothing, Any]) {
    def apply[E1 >: E, B](use: A => IO[E1, B]): IO[E1, B] =
      ZIO.bracketFork(acquire, release, use)
  }

}
