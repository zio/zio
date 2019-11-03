package zio

import zio.internal.{ Executor, Platform }

object UIO {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  final def absolve[A](v: UIO[Either[Nothing, A]]): UIO[A] =
    ZIO.absolve(v)

  /**
   * @see See [[zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see See [[zio.ZIO.apply]]
   */
  final def apply[A](a: => A): UIO[A] = ZIO.effectTotal(a)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[A](acquire: UIO[A]): ZIO.BracketAcquire[Any, Nothing, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[A, B](acquire: UIO[A], release: A => UIO[Any], use: A => UIO[B]): UIO[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[A](acquire: UIO[A]): ZIO.BracketExitAcquire[Any, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[A, B](acquire: UIO[A], release: (A, Exit[Nothing, B]) => UIO[Any], use: A => UIO[B]): UIO[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkDaemon]]
   */
  final def checkDaemon[A](f: DaemonStatus => UIO[A]): UIO[A] =
    ZIO.checkDaemon(f)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[A](f: InterruptStatus => UIO[A]): UIO[A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[A](f: TracingStatus => UIO[A]): UIO[A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  final def children: UIO[Set[Fiber[Any, Any]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  final def collectAll[A](in: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[A](as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[A](n: Int)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  final def collectAllSuccesses[A](in: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  final def collectAllSuccessesPar[A](as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  final def collectAllSuccessesParN[A](n: Int)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  final def collectAllWith[A, B](in: Iterable[UIO[A]])(f: PartialFunction[A, B]): UIO[List[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  final def collectAllWithPar[A, B](as: Iterable[UIO[A]])(f: PartialFunction[A, B]): UIO[List[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  final def collectAllWithParN[A, B](n: Int)(as: Iterable[UIO[A]])(f: PartialFunction[A, B]): UIO[List[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[A](f: Fiber.Descriptor => UIO[A]): UIO[A] =
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
  final def done[A](r: Exit[Nothing, A]): UIO[A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[A](register: (UIO[A] => Unit) => Unit): UIO[A] =
    ZIO.effectAsync(register)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (UIO[A] => Unit) => Option[UIO[A]]): UIO[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[A](register: (UIO[A] => Unit) => UIO[Any]): UIO[A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[A](register: (UIO[A] => Unit) => Either[Canceler[Any], UIO[A]]): UIO[A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  final def effectSuspendTotal[A](uio: => UIO[A]): UIO[A] = new ZIO.EffectSuspendTotalWith(_ => uio)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  final def effectSuspendTotalWith[A](p: Platform => UIO[A]): UIO[A] = new ZIO.EffectSuspendTotalWith(p)

  /**
   * @see [[zio.ZIO.firstSuccessOf]]
   */
  final def firstSuccessOf[A](uio: UIO[A], rest: Iterable[UIO[A]]): UIO[A] = ZIO.firstSuccessOf(uio, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  final def flatten[A](uio: UIO[UIO[A]]): UIO[A] =
    ZIO.flatten(uio)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => UIO[S]): UIO[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  final def forkAll[A](as: Iterable[UIO[A]]): UIO[Fiber[Nothing, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[A](as: Iterable[UIO[A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  final def foreach[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  final def foreach_[A](as: Iterable[A])(f: A => UIO[Any]): UIO[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[A](as: Iterable[A])(f: A => UIO[Any]): UIO[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[A, B](n: Int)(as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[A](n: Int)(as: Iterable[A])(f: A => UIO[Any]): UIO[Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Nothing, A]): UIO[A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Nothing, A]): UIO[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: UIO[Fiber[Nothing, A]]): UIO[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see [[zio.ZIO.fromFunction]]
   */
  final def fromFunction[A](f: Any => A): UIO[A] = ZIO.fromFunction(f)

  /**
   * @see [[zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[A](f: Any => UIO[A]): UIO[A] = ZIO.fromFunctionM(f)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Nothing]): UIO[Nothing] = ZIO.halt(cause)

  /**
   * @see [[zio.ZIO.haltWith]]
   */
  final def haltWith(function: (() => ZTrace) => Cause[Nothing]): UIO[Nothing] = ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.identity]]
   */
  final def identity: UIO[Any] = ZIO.identity

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  final def interruptAs(fiberId: FiberId): UIO[Nothing] = ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  final def interruptible[A](uio: UIO[A]): UIO[A] =
    ZIO.interruptible(uio)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.interruptibleMask(k)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  final def left[A](a: A): UIO[Either[A, Nothing]] = ZIO.left(a)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  final def lock[A](executor: Executor)(uio: UIO[A]): UIO[A] =
    ZIO.lock(executor)(uio)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[A, B](in: Iterable[UIO[A]])(zero: B)(f: (B, A) => B): UIO[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[A, B](in: Iterable[UIO[A]])(zero: B)(f: (B, A) => B): UIO[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.none]]
   */
  final val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  final def raceAll[A](uio: UIO[A], uios: Iterable[UIO[A]]): UIO[A] =
    ZIO.raceAll(uio, uios)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[A](a: UIO[A], as: Iterable[UIO[A]])(f: (A, A) => A): UIO[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[A](a: UIO[A], as: Iterable[UIO[A]])(f: (A, A) => A): UIO[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[A](n: Int)(effect: UIO[A]): Iterable[UIO[A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  final def reserve[A, B](reservation: UIO[Reservation[Any, Nothing, A]])(use: A => UIO[B]): UIO[B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[B](b: B): UIO[Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  final def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   *  See [[zio.ZIO.sequence]]
   */
  final def sequence[A](in: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequence(in)

  /**
   * @see See [[zio.ZIO.sequencePar]]
   */
  final def sequencePar[A](as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  final def sequenceParN[A](n: Int)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[A](a: A): UIO[Option[A]] = ZIO.some(a)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  @deprecated("use effectTotal", "1.0.0")
  final def succeedLazy[A](a: => A): UIO[A] =
    effectTotal(a)

  @deprecated("use effectSuspendTotal", "1.0.0")
  final def suspend[A](uio: => UIO[A]): UIO[A] = effectSuspendTotalWith(_ => uio)

  @deprecated("use effectSuspendTotalWith", "1.0.0")
  final def suspendWith[A](p: Platform => UIO[A]): UIO[A] = effectSuspendTotalWith(p)

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  final def traced[A](uio: UIO[A]): UIO[A] = ZIO.traced(uio)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  final def traverse[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traverse_]]
   */
  final def traverse_[A](as: Iterable[A])(f: A => UIO[Any]): UIO[Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  final def traversePar[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.traversePar(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[A](as: Iterable[A])(f: A => UIO[Any]): UIO[Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN]]
   */
  final def traverseParN[A, B](
    n: Int
  )(as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[A](
    n: Int
  )(as: Iterable[A])(f: A => UIO[Any]): UIO[Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  final def uninterruptible[A](uio: UIO[A]): UIO[A] =
    ZIO.uninterruptible(uio)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see [[zio.ZIO.unsandbox]]
   */
  final def unsandbox[A](v: IO[Cause[Nothing], A]): UIO[A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  final def untraced[A](uio: UIO[A]): UIO[A] = ZIO.untraced(uio)

  /**
   * @see See [[zio.ZIO.when]]
   */
  final def when(b: Boolean)(uio: UIO[Any]): UIO[Unit] =
    ZIO.when(b)(uio)

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
  final def whenM(b: UIO[Boolean])(uio: UIO[Any]): UIO[Unit] =
    ZIO.whenM(b)(uio)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

}
