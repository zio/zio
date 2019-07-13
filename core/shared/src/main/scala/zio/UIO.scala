package zio

import zio.internal.{ Executor, Platform }

object UIO {

  /**
   * See [[zio.ZIO.absolve]]
   */
  final def absolve[A](v: UIO[Either[Nothing, A]]): UIO[A] =
    ZIO.absolve(v)

  /**
   * See [[zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * See [[zio.ZIO.apply]]
   */
  def apply[A](a: => A): UIO[A] = ZIO.effectTotal(a)

  /**
   * See [[zio.ZIO.bracket]]
   */
  final def bracket[A](acquire: UIO[A]): ZIO.BracketAcquire[Any, Nothing, A] =
    ZIO.bracket(acquire)

  /**
   * See [[zio.ZIO.bracket]]
   */
  final def bracket[A, B](acquire: UIO[A], release: A => UIO[_], use: A => UIO[B]): UIO[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * See [[zio.ZIO.bracketExit]]
   */
  final def bracketExit[A](acquire: UIO[A]): ZIO.BracketExitAcquire[Any, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[zio.ZIO.bracketExit]]
   */
  final def bracketExit[A, B](acquire: UIO[A], release: (A, Exit[Nothing, B]) => UIO[_], use: A => UIO[B]): UIO[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[A](f: InterruptStatus => UIO[A]): UIO[A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[zio.ZIO.checkSupervised]]
   */
  final def checkSupervised[A](f: SuperviseStatus => UIO[A]): UIO[A] =
    ZIO.checkSupervised(f)

  /**
   * See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[A](f: TracingStatus => UIO[A]): UIO[A] =
    ZIO.checkTraced(f)

  /**
   * See [[zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[zio.ZIO.collectAll]]
   */
  final def collectAll[A](in: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[A](as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[A](n: Long)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[A](f: Fiber.Descriptor => UIO[A]): UIO[A] =
    ZIO.descriptorWith(f)

  /**
   * See [[zio.ZIO.die]]
   */
  final def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * See [[zio.ZIO.dieMessage]]
   */
  final def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * See [[zio.ZIO.done]]
   */
  final def done[A](r: Exit[Nothing, A]): UIO[A] = ZIO.done(r)

  /**
   * See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[A](register: (UIO[A] => Unit) => Unit): UIO[A] =
    ZIO.effectAsync(register)

  /**
   * See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (UIO[A] => Unit) => Option[UIO[A]]): UIO[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[A](register: (UIO[A] => Unit) => UIO[_]): UIO[A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[A](register: (UIO[A] => Unit) => Either[Canceler, UIO[A]]): UIO[A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[zio.ZIO.flatten]]
   */
  final def flatten[A](uio: UIO[UIO[A]]): UIO[A] =
    ZIO.flatten(uio)

  /**
   * See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => UIO[S]): UIO[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[zio.ZIO.forkAll]]
   */
  final def forkAll[A](as: Iterable[UIO[A]]): UIO[Fiber[Nothing, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[A](as: Iterable[UIO[A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * See [[zio.ZIO.foreach]]
   */
  final def foreach[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.foreach(in)(f)

  /**
   * See [[zio.ZIO.foreach_]]
   */
  final def foreach_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[A, B](n: Long)(as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[A](n: Long)(as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * See [[zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Nothing, A]): UIO[A] =
    ZIO.fromEither(v)

  /**
   * See [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Nothing, A]): UIO[A] =
    ZIO.fromFiber(fiber)

  /**
   * See [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: UIO[Fiber[Nothing, A]]): UIO[A] =
    ZIO.fromFiberM(fiber)

  /**
   * See [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Nothing]): UIO[Nothing] = ZIO.halt(cause)

  /**
   * See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * See [[zio.ZIO.interruptible]]
   */
  final def interruptible[A](uio: UIO[A]): UIO[A] =
    ZIO.interruptible(uio)

  /**
   * See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[zio.ZIO.lock]]
   */
  final def lock[A](executor: Executor)(uio: UIO[A]): UIO[A] =
    ZIO.lock(executor)(uio)

  /**
   * See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[A, B](in: Iterable[UIO[A]])(zero: B)(f: (B, A) => B): UIO[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[A, B](in: Iterable[UIO[A]])(zero: B)(f: (B, A) => B): UIO[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * See [[zio.ZIO.raceAll]]
   */
  final def raceAll[A](uio: UIO[A], uios: Iterable[UIO[A]]): UIO[A] =
    ZIO.raceAll(uio, uios)

  /**
   * See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[A](a: UIO[A], as: Iterable[UIO[A]])(f: (A, A) => A): UIO[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[A](a: UIO[A], as: Iterable[UIO[A]])(f: (A, A) => A): UIO[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[zio.ZIO.reserve]]
   */
  final def reserve[A, B](reservation: UIO[Reservation[Any, Nothing, A]])(use: A => UIO[B]): UIO[B] =
    ZIO.reserve(reservation)(use)

  /**
   * See [[zio.ZIO.runtime]]
   */
  final def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   *  See [[zio.ZIO.sequence]]
   */
  final def sequence[A](in: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequence(in)

  /**
   * See [[zio.ZIO.sequencePar]]
   */
  final def sequencePar[A](as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  final def sequenceParN[A](n: Long)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   * See [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * See [[zio.ZIO.succeedLazy]]
   */
  final def succeedLazy[A](a: => A): UIO[A] = ZIO.succeedLazy(a)

  /**
   * See [[zio.ZIO.interruptChildren]]
   */
  final def interruptChildren[A](uio: UIO[A]): UIO[A] =
    ZIO.interruptChildren(uio)

  /**
   * See [[zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[A](uio: UIO[A])(supervisor: IndexedSeq[Fiber[_, _]] => UIO[_]): UIO[A] =
    ZIO.handleChildrenWith(uio)(supervisor)

  /**
   * See [[zio.ZIO.supervised]]
   */
  final def supervised[A](uio: UIO[A]): UIO[A] =
    ZIO.supervised(uio)

  /**
   * See [[zio.ZIO.superviseStatus]]
   */
  final def superviseStatus[A](status: SuperviseStatus)(uio: UIO[A]): UIO[A] =
    ZIO.superviseStatus(status)(uio)

  /**
   * See [[zio.ZIO.suspend]]
   */
  final def suspend[A](uio: => UIO[A]): UIO[A] =
    ZIO.suspend(uio)

  /**
   * [[zio.ZIO.suspendWith]]
   */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

  /**
   * See [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * See [[zio.ZIO.traced]]
   */
  final def traced[A](uio: UIO[A]): UIO[A] = ZIO.traced(uio)

  /**
   * See [[zio.ZIO.traverse]]
   */
  final def traverse[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.traverse(in)(f)

  /**
   * See [[zio.ZIO.traverse_]]
   */
  final def traverse_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.traverse_(as)(f)

  /**
   * See [[zio.ZIO.traversePar]]
   */
  final def traversePar[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.traversePar(in)(f)

  /**
   * See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * See [[zio.ZIO.traverseParN]]
   */
  final def traverseParN[A, B](
    n: Long
  )(as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[A](
    n: Long
  )(as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * See [[zio.ZIO.interruptibleMask]]
   */
  final def uninterruptible[A](uio: UIO[A]): UIO[A] =
    ZIO.uninterruptible(uio)

  /**
   * See [[zio.ZIO.unsupervised]].
   */
  final def unsupervised[R, E, A](uio: UIO[A]): UIO[A] =
    ZIO.unsupervised(uio)

  /**
   * See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[zio.ZIO.untraced]]
   */
  final def untraced[A](uio: UIO[A]): UIO[A] = ZIO.untraced(uio)

  /**
   * See [[zio.ZIO.when]]
   */
  final def when(b: Boolean)(uio: UIO[_]): UIO[Unit] =
    ZIO.when(b)(uio)

  /**
   * See [[zio.ZIO.whenM]]
   */
  final def whenM(b: UIO[Boolean])(uio: UIO[_]): UIO[Unit] =
    ZIO.whenM(b)(uio)

  /**
   * See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

}
