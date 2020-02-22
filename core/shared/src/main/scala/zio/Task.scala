package zio

import scala.concurrent.ExecutionContext

import zio.internal.{ Executor, Platform }

object Task {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  def absolve[A](v: Task[Either[Throwable, A]]): Task[A] =
    ZIO.absolve(v)

  /**
   * @see See [[zio.ZIO.allowInterrupt]]
   */
  def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see See [[zio.ZIO.apply]]
   */
  def apply[A](a: => A): Task[A] = ZIO.apply(a)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  def bracket[A](acquire: Task[A]): ZIO.BracketAcquire[Any, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  def bracket[A, B](acquire: Task[A], release: A => UIO[Any], use: A => Task[B]): Task[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  def bracketExit[A](acquire: Task[A]): ZIO.BracketExitAcquire[Any, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  def bracketExit[A, B](
    acquire: Task[A],
    release: (A, Exit[Throwable, B]) => UIO[Any],
    use: A => Task[B]
  ): Task[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See bracketFork [[zio.ZIO]]
   */
  def bracketFork[A](acquire: Task[A]): ZIO.BracketForkAcquire[Any, Throwable, A] =
    ZIO.bracketFork(acquire)

  /**
   * @see See bracketFork [[zio.ZIO]]
   */
  def bracketFork[A, B](acquire: Task[A], release: A => UIO[Any], use: A => Task[B]): Task[B] =
    ZIO.bracketFork(acquire, release, use)

  /**
   * @see See bracketForkExit [[zio.ZIO]]
   */
  def bracketForkExit[A](acquire: Task[A]): ZIO.BracketForkExitAcquire[Any, Throwable, A] =
    ZIO.bracketForkExit(acquire)

  /**
   * @see See bracketForkExit [[zio.ZIO]]
   */
  def bracketForkExit[A, B](
    acquire: Task[A],
    release: (A, Exit[Throwable, B]) => UIO[Any],
    use: A => Task[B]
  ): Task[B] =
    ZIO.bracketForkExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[A](f: InterruptStatus => Task[A]): Task[A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  def checkTraced[A](f: TracingStatus => Task[A]): Task[A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  def children: UIO[Iterable[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  def collectAll[A](in: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  def collectAllPar[A](as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  def collectAllParN[A](n: Int)(as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[A](in: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[A](as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  def collectAllSuccessesParN[A](n: Int)(as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[A, B](in: Iterable[Task[A]])(f: PartialFunction[A, B]): Task[List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[A, B](as: Iterable[Task[A]])(f: PartialFunction[A, B]): Task[List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  def collectAllWithParN[A, B](n: Int)(as: Iterable[Task[A]])(f: PartialFunction[A, B]): Task[List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.die]]
   */
  def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * @see See [[zio.ZIO.dieMessage]]
   */
  def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * @see See [[zio.ZIO.done]]
   */
  def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[A](f: Fiber.Descriptor => Task[A]): Task[A] =
    ZIO.descriptorWith(f)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  def effectAsync[A](register: (Task[A] => Unit) => Unit, blockingOn: List[Fiber.Id] = Nil): Task[A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  def effectAsyncMaybe[A](
    register: (Task[A] => Unit) => Option[Task[A]],
    blockingOn: List[Fiber.Id] = Nil
  ): Task[A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  def effectAsyncM[A](register: (Task[A] => Unit) => Task[Any]): Task[A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  def effectAsyncInterrupt[A](
    register: (Task[A] => Unit) => Either[Canceler[Any], Task[A]],
    blockingOn: List[Fiber.Id] = Nil
  ): Task[A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see See [[zio.RIO.effectSuspend]]
   */
  def effectSuspend[A](task: => Task[A]): Task[A] = ZIO.effectSuspend(task)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  def effectSuspendTotal[A](task: => Task[A]): Task[A] = ZIO.effectSuspendTotal(task)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  def effectSuspendTotalWith[A](p: (Platform, Fiber.Id) => Task[A]): Task[A] = ZIO.effectSuspendTotalWith(p)

  /**
   * @see See [[zio.RIO.effectSuspendWith]]
   */
  def effectSuspendWith[A](p: (Platform, Fiber.Id) => Task[A]): Task[A] = ZIO.effectSuspendWith(p)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.fail]]
   */
  def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  val fiberId: UIO[Fiber.Id] = ZIO.fiberId

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[A](
    task: Task[A],
    rest: Iterable[Task[A]]
  ): Task[A] =
    ZIO.firstSuccessOf(task, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  def flatten[A](task: Task[Task[A]]): Task[A] =
    ZIO.flatten(task)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => Task[S]): Task[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foldRight]]
   */
  def foldRight[S, A](in: Iterable[A])(zero: S)(f: (A, S) => Task[S]): Task[S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  def foreach[A, B](in: Iterable[A])(f: A => Task[B]): Task[List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  def foreachPar[A, B](as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  def foreachParN[A, B](n: Int)(as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  def foreach_[A](as: Iterable[A])(f: A => Task[Any]): Task[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  def foreachPar_[A, B](as: Iterable[A])(f: A => Task[Any]): Task[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  def foreachParN_[A, B](n: Int)(as: Iterable[A])(f: A => Task[Any]): Task[Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  def forkAll[A](as: Iterable[Task[A]]): UIO[Fiber.Synthetic[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  def forkAll_[A](as: Iterable[Task[A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  def fromEither[A](v: => Either[Throwable, A]): Task[A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  def fromFiber[A](fiber: => Fiber[Throwable, A]): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  def fromFiberM[A](fiber: Task[Fiber[Throwable, A]]): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see [[zio.ZIO.fromFunction]]
   */
  def fromFunction[A](f: Any => A): Task[A] = ZIO.fromFunction(f)

  /**
   * @see See [[zio.ZIO.fromFutureInterrupt]]
   */
  def fromFutureInterrupt[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFutureInterrupt(make)

  /**
   * @see [[zio.ZIO.fromFunctionFuture]]
   */
  def fromFunctionFuture[A](f: Any => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFunctionFuture(f)

  /**
   * @see [[zio.ZIO.fromFunctionM]]
   */
  def fromFunctionM[A](f: Any => Task[A]): Task[A] = ZIO.fromFunctionM(f)

  /**
   * @see See [[zio.ZIO.fromFuture]]
   */
  def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  def halt(cause: Cause[Throwable]): Task[Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  def haltWith[E <: Throwable](function: (() => ZTrace) => Cause[E]): Task[Nothing] =
    ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.identity]]
   */
  def identity: Task[Any] = ZIO.identity

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  def interruptAs(fiberId: Fiber.Id): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  def interruptible[A](task: Task[A]): Task[A] =
    ZIO.interruptible(task)

  /**
   * @see See [[zio.ZIO.interruptibleDisconnect]]
   */
  def interruptibleDisconnect[A](task: Task[A]): Task[A] =
    ZIO.interruptibleDisconnect(task)

  /**
   * @see See [[zio.ZIO.interruptibleDisconnectMask]]
   */
  def interruptibleDisconnectMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.interruptibleDisconnectMask(k)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.interruptibleMask(k)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  def left[A](a: A): Task[Either[A, Nothing]] = ZIO.left(a)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  def lock[A](executor: Executor)(task: Task[A]): Task[A] =
    ZIO.lock(executor)(task)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  def mapN[A, B, C](task1: Task[A], task2: Task[B])(f: (A, B) => C): Task[C] =
    ZIO.mapN(task1, task2)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  def mapN[A, B, C, D](task1: Task[A], task2: Task[B], task3: Task[C])(f: (A, B, C) => D): Task[D] =
    ZIO.mapN(task1, task2, task3)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  def mapN[A, B, C, D, F](task1: Task[A], task2: Task[B], task3: Task[C], task4: Task[D])(
    f: (A, B, C, D) => F
  ): Task[F] =
    ZIO.mapN(task1, task2, task3, task4)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  def mapParN[A, B, C](task1: Task[A], task2: Task[B])(f: (A, B) => C): Task[C] =
    ZIO.mapParN(task1, task2)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  def mapParN[A, B, C, D](task1: Task[A], task2: Task[B], task3: Task[C])(f: (A, B, C) => D): Task[D] =
    ZIO.mapParN(task1, task2, task3)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  def mapParN[A, B, C, D, F](task1: Task[A], task2: Task[B], task3: Task[C], task4: Task[D])(
    f: (A, B, C, D) => F
  ): Task[F] =
    ZIO.mapParN(task1, task2, task3, task4)(f)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  def mergeAll[A, B](in: Iterable[Task[A]])(zero: B)(f: (B, A) => B): Task[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[A, B](in: Iterable[Task[A]])(zero: B)(f: (B, A) => B): Task[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.none]]
   */
  val none: Task[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.partitionM]]
   */
  def partitionM[A, B](in: Iterable[A])(f: A => Task[B]): Task[(List[Throwable], List[B])] =
    ZIO.partitionM(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMPar]]
   */
  def partitionMPar[A, B](in: Iterable[A])(f: A => Task[B]): Task[(List[Throwable], List[B])] =
    ZIO.partitionMPar(in)(f)

  /**
   * @see See [[zio.ZIO.partitionMParN]]
   */
  def partitionMParN[A, B](n: Int)(in: Iterable[A])(f: A => Task[B]): Task[(List[Throwable], List[B])] =
    ZIO.partitionMParN(n)(in)(f)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  def raceAll[A](task: Task[A], ios: Iterable[Task[A]]): Task[A] =
    ZIO.raceAll(task, ios)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  def reduceAll[A](a: Task[A], as: Iterable[Task[A]])(f: (A, A) => A): Task[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[A](a: Task[A], as: Iterable[Task[A]])(f: (A, A) => A): Task[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[A](n: Int)(effect: Task[A]): Iterable[Task[A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.require]]
   */
  def require[A](error: Throwable): Task[Option[A]] => Task[A] =
    ZIO.require[Any, Throwable, A](error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  def reserve[A, B](reservation: Task[Reservation[Any, Throwable, A]])(use: A => Task[B]): Task[B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[B](b: B): Task[Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   *  See [[zio.ZIO.sequence]]
   */
  def sequence[A](in: Iterable[Task[A]]): Task[List[A]] =
    ZIO.sequence(in)

  /**
   *  See [[zio.ZIO.sequencePar]]
   */
  def sequencePar[A](as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  def sequenceParN[A](n: Int)(as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[A](a: A): Task[Option[A]] = ZIO.some(a)

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  def traced[A](task: Task[A]): Task[A] = ZIO.traced(task)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  def traverse[A, B](in: Iterable[A])(f: A => Task[B]): Task[List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  def traversePar[A, B](as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  def traverseParN[A, B](
    n: Int
  )(as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverse_]]
   */
  def traverse_[A](as: Iterable[A])(f: A => Task[Any]): Task[Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  def traversePar_[A](as: Iterable[A])(f: A => Task[Any]): Task[Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  def traverseParN_[A](
    n: Int
  )(as: Iterable[A])(f: A => Task[Any]): Task[Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[A](task: Task[A]): Task[A] =
    ZIO.uninterruptible(task)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see [[zio.ZIO.unsandbox]]
   */
  def unsandbox[A](v: IO[Cause[Throwable], A]): Task[A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  def untraced[A](task: Task[A]): Task[A] = ZIO.untraced(task)

  /**
   * @see See [[zio.ZIO.when]]
   */
  def when(b: Boolean)(task: Task[Any]): Task[Unit] =
    ZIO.when(b)(task)

  /**
   * @see See [[zio.ZIO.whenCase]]
   */
  def whenCase[R, E, A](a: A)(pf: PartialFunction[A, ZIO[R, E, Any]]): ZIO[R, E, Unit] =
    ZIO.whenCase(a)(pf)

  /**
   * @see See [[zio.ZIO.whenCaseM]]
   */
  def whenCaseM[R, E, A](a: ZIO[R, E, A])(pf: PartialFunction[A, ZIO[R, E, Any]]): ZIO[R, E, Unit] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see See [[zio.ZIO.whenM]]
   */
  def whenM(b: Task[Boolean])(task: Task[Any]): Task[Unit] =
    ZIO.whenM(b)(task)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  val yieldNow: UIO[Unit] = ZIO.yieldNow
}
