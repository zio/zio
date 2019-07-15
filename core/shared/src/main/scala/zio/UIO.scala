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
  def apply[A](a: => A): UIO[A] = ZIO.effectTotal(a)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[A](acquire: UIO[A]): ZIO.BracketAcquire[Any, Nothing, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[A, B](acquire: UIO[A], release: A => UIO[_], use: A => UIO[B]): UIO[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[A](acquire: UIO[A]): ZIO.BracketExitAcquire[Any, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[A, B](acquire: UIO[A], release: (A, Exit[Nothing, B]) => UIO[_], use: A => UIO[B]): UIO[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[A](f: InterruptStatus => UIO[A]): UIO[A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkSupervised]]
   */
  final def checkSupervised[A](f: SuperviseStatus => UIO[A]): UIO[A] =
    ZIO.checkSupervised(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[A](f: TracingStatus => UIO[A]): UIO[A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

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
  final def collectAllParN[A](n: Long)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllParN(n)(as)

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
  final def effectAsyncM[A](register: (UIO[A] => Unit) => UIO[_]): UIO[A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[A](register: (UIO[A] => Unit) => Either[Canceler, UIO[A]]): UIO[A] =
    ZIO.effectAsyncInterrupt(register)

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
  final def foreach_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[A, B](n: Long)(as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[A](n: Long)(as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
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
   * @see See [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Nothing]): UIO[Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

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
   * @see See [[zio.ZIO.reserve]]
   */
  final def reserve[A, B](reservation: UIO[Reservation[Any, Nothing, A]])(use: A => UIO[B]): UIO[B] =
    ZIO.reserve(reservation)(use)

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
  final def sequenceParN[A](n: Long)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * @see See [[zio.ZIO.succeedLazy]]
   */
  final def succeedLazy[A](a: => A): UIO[A] = ZIO.succeedLazy(a)

  /**
   * @see See [[zio.ZIO.interruptChildren]]
   */
  final def interruptChildren[A](uio: UIO[A]): UIO[A] =
    ZIO.interruptChildren(uio)

  /**
   * @see See [[zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[A](uio: UIO[A])(supervisor: IndexedSeq[Fiber[_, _]] => UIO[_]): UIO[A] =
    ZIO.handleChildrenWith(uio)(supervisor)

  /**
   * @see See [[zio.ZIO.supervised]]
   */
  final def supervised[A](uio: UIO[A]): UIO[A] =
    ZIO.supervised(uio)

  /**
   * @see See [[zio.ZIO.superviseStatus]]
   */
  final def superviseStatus[A](status: SuperviseStatus)(uio: UIO[A]): UIO[A] =
    ZIO.superviseStatus(status)(uio)

  /**
   * @see See [[zio.ZIO.suspend]]
   */
  final def suspend[A](uio: => UIO[A]): UIO[A] =
    ZIO.suspend(uio)

  /**
   * [[zio.ZIO.suspendWith]]
   */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

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
  final def traverse_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
   */
  final def traversePar[A, B](in: Iterable[A])(f: A => UIO[B]): UIO[List[B]] =
    ZIO.traversePar(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[A](as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN]]
   */
  final def traverseParN[A, B](
    n: Long
  )(as: Iterable[A])(fn: A => UIO[B]): UIO[List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[A](
    n: Long
  )(as: Iterable[A])(f: A => UIO[_]): UIO[Unit] =
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
   * @see See [[zio.ZIO.unsupervised]].
   */
  final def unsupervised[R, E, A](uio: UIO[A]): UIO[A] =
    ZIO.unsupervised(uio)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  final def untraced[A](uio: UIO[A]): UIO[A] = ZIO.untraced(uio)

  /**
   * @see See [[zio.ZIO.when]]
   */
  final def when(b: Boolean)(uio: UIO[_]): UIO[Unit] =
    ZIO.when(b)(uio)

  /**
   * @see See [[zio.ZIO.whenM]]
   */
  final def whenM(b: UIO[Boolean])(uio: UIO[_]): UIO[Unit] =
    ZIO.whenM(b)(uio)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

}
