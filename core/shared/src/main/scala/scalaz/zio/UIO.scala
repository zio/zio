package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.internal.{ Executor, Platform }

object UIO {

  /**
   * See [[scalaz.zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * See [[scalaz.zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * See [[scalaz.zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * See [[scalaz.zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * See [[scalaz.zio.ZIO.absolve]]
   */
  final def absolve[A](v: UIO[Either[Nothing, A]]): UIO[A] =
    ZIO.absolve(v)

  /**
   * See [[scalaz.zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  def apply[A](a: => A): UIO[A] = effectTotal(a)

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[A](acquire: UIO[A]): ZIO.BracketAcquire[Any, Nothing, A] =
    ZIO.bracket(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[A, B](acquire: UIO[A], release: A => UIO[_], use: A => UIO[B]): UIO[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[A](acquire: UIO[A]): ZIO.BracketExitAcquire[Any, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[A, B](acquire: UIO[A], release: (A, Exit[Nothing, B]) => UIO[_], use: A => UIO[B]): UIO[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[A](f: Boolean => UIO[A]): UIO[A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[scalaz.zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[scalaz.zio.ZIO.collectAll]]
   */
  final def collectAll[A](in: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[scalaz.zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[A](as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[scalaz.zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[A](n: Long)(as: Iterable[UIO[A]]): UIO[List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * See [[scalaz.zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[scalaz.zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[A](f: Fiber.Descriptor => UIO[A]): UIO[A] =
    ZIO.descriptorWith(f)

  /**
   * See [[scalaz.zio.ZIO.die]]
   */
  final def die(t: Throwable): UIO[Nothing] = ZIO.die(t)

  /**
   * See [[scalaz.zio.ZIO.dieMessage]]
   */
  final def dieMessage(message: String): UIO[Nothing] = ZIO.dieMessage(message)

  /**
   * See [[scalaz.zio.ZIO.done]]
   */
  final def done[A](r: Exit[Nothing, A]): UIO[A] = ZIO.done(r)

  /**
   * See [[scalaz.zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * [[scalaz.zio.ZIO.suspendWith]]
   */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

  /**
   * See [[scalaz.zio.ZIO.effectAsync]]
   */
  final def effectAsync[A](register: (UIO[A] => Unit) => Unit): UIO[A] =
    ZIO.effectAsync(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (UIO[A] => Unit) => Option[UIO[A]]): UIO[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[A](register: (UIO[A] => Unit) => UIO[_]): UIO[A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[A](register: (UIO[A] => Unit) => Either[Canceler, UIO[A]]): UIO[A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[scalaz.zio.ZIO.flatten]]
   */
  final def flatten[A](uio: UIO[UIO[A]]): UIO[A] =
    ZIO.flatten(uio)

  /**
   * See [[scalaz.zio.ZIO.foldLeft]]
   */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => UIO[S]): UIO[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.forkAll]]
   */
  final def forkAll[A](as: Iterable[UIO[A]]): UIO[Fiber[Nothing, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[scalaz.zio.ZIO.forkAll_]]
   */
  final def forkAll_[A](as: Iterable[UIO[A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * See [[scalaz.zio.ZIO.fromFunction]]
   */
  final def fromFunction[A](f: Any => A): UIO[A] =
    ZIO.fromFunction(f)

  /**
   * See [[scalaz.zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[A](f: Any => UIO[A]): UIO[A] =
    ZIO.fromFunctionM(f)

  /**
   * See [[scalaz.zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Nothing, A]): UIO[A] =
    ZIO.fromEither(v)

  /**
   * See [[scalaz.zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Nothing, A]): UIO[A] =
    ZIO.fromFiber(fiber)

  /**
   * See [[scalaz.zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: UIO[Fiber[Nothing, A]]): UIO[A] =
    ZIO.fromFiberM(fiber)

  /**
   * See [[scalaz.zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Nothing]): UIO[Nothing] = ZIO.halt(cause)

  /**
   * See [[scalaz.zio.ZIO.interruptible]]
   */
  final def interruptible[A](uio: UIO[A]): UIO[A] =
    ZIO.interruptible(uio)

  /**
   * See [[scalaz.zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.lock]]
   */
  final def lock[A](executor: Executor)(uio: UIO[A]): UIO[A] =
    ZIO.lock(executor)(uio)

  /**
   * See [[scalaz.zio.ZIO.mergeAll]]
   */
  final def mergeAll[A, B](in: Iterable[UIO[A]])(zero: B)(f: (B, A) => B): UIO[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[A, B](in: Iterable[UIO[A]])(zero: B)(f: (B, A) => B): UIO[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.raceAll]]
   */
  final def raceAll[A](uio: UIO[A], uios: Iterable[UIO[A]]): UIO[A] =
    ZIO.raceAll(uio, uios)

  /**
   * See [[scalaz.zio.ZIO.reduceAll]]
   */
  final def reduceAll[A](a: UIO[A], as: Iterable[UIO[A]])(f: (A, A) => A): UIO[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[A](a: UIO[A], as: Iterable[UIO[A]])(f: (A, A) => A): UIO[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.reserve]]
   */
  def reserve[A, B](reservation: UIO[Reservation[Any, Nothing, A]])(use: A => UIO[B]): UIO[B] =
    ZIO.reserve(reservation)(use)

  /**
   * See [[scalaz.zio.ZIO.runtime]]
   */
  final def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   * See [[scalaz.zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * See [[scalaz.zio.ZIO.succeedLazy]]
   */
  final def succeedLazy[A](a: => A): UIO[A] = ZIO.succeedLazy(a)

  /**
   * See [[scalaz.zio.ZIO.supervise]]
   */
  final def supervise[A](uio: UIO[A]): UIO[A] =
    ZIO.supervise(uio)

  /**
   * See [[scalaz.zio.ZIO.supervised]]
   */
  final def supervised[A](uio: UIO[A]): UIO[A] =
    ZIO.supervised(uio)

  /**
   * See [[scalaz.zio.ZIO.superviseWith]]
   */
  final def superviseWith[A](uio: UIO[A])(supervisor: IndexedSeq[Fiber[_, _]] => UIO[_]): UIO[A] =
    ZIO.superviseWith(uio)(supervisor)

  /**
   * See [[scalaz.zio.ZIO.suspend]]
   */
  final def suspend[A](uio: => UIO[A]): UIO[A] =
    ZIO.suspend(uio)

  /**
   * See [[scalaz.zio.ZIO.interruptibleMask]]
   */
  final def uninterruptible[A](uio: UIO[A]): UIO[A] =
    ZIO.uninterruptible(uio)

  /**
   * See [[scalaz.zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => UIO[A]): UIO[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.when]]
   */
  final def when(b: Boolean)(uio: UIO[_]): UIO[Unit] =
    ZIO.when(b)(uio)

  /**
   * See [[scalaz.zio.ZIO.whenM]]
   */
  final def whenM(b: UIO[Boolean])(uio: UIO[_]): UIO[Unit] =
    ZIO.whenM(b)(uio)

}
