package zio

import zio.Exit.Cause
import zio.internal.{Executor, Platform}

import scala.concurrent.ExecutionContext

object IO {

  /**
   * See [[scalaz.zio.ZIO.absolve]]
   */
  final def absolve[E, A](v: IO[E, Either[E, A]]): IO[E, A] =
    ZIO.absolve(v)

  /**
   * See [[scalaz.zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  def apply[A](a: => A): Task[A] = effect(a)

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[E, A](acquire: IO[E, A]): BracketAcquire[E, A] =
    new BracketAcquire(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[E, A, B](acquire: IO[E, A], release: A => UIO[_], use: A => IO[E, B]): IO[E, B] =
    ZIO.bracket(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[E, A](acquire: IO[E, A]): ZIO.BracketExitAcquire[Any, E, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[E, A, B](acquire: IO[E, A],
                                 release: (A, Exit[E, B]) => UIO[_],
                                 use:     A => IO[E, B]): IO[E, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[E, A](f: InterruptStatus => IO[E, A]): IO[E, A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[scalaz.zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[scalaz.zio.ZIO.collectAll]]
   */
  final def collectAll[E, A](in: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[scalaz.zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[E, A](as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[scalaz.zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[E, A](n: Long)(as: Iterable[IO[E, A]]): IO[E, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * See [[scalaz.zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[scalaz.zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[E, A](f: Fiber.Descriptor => IO[E, A]): IO[E, A] =
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
  final def done[E, A](r: Exit[E, A]): IO[E, A] = ZIO.done(r)

  /**
   * See [[scalaz.zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * See [[scalaz.zio.ZIO.effectAsync]]
   */
  final def effectAsync[E, A](register: (IO[E, A] => Unit) => Unit): IO[E, A] =
    ZIO.effectAsync(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[E, A](register: (IO[E, A] => Unit) => Either[Canceler, IO[E, A]]): IO[E, A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[E, A](register: (IO[E, A] => Unit) => UIO[_]): IO[E, A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[E, A](register: (IO[E, A] => Unit) => Option[IO[E, A]]): IO[E, A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[scalaz.zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * See [[scalaz.zio.ZIO.fail]]
   */
  final def fail[E](error: E): IO[E, Nothing] = ZIO.fail(error)

  /**
   * See [[scalaz.zio.ZIO.flatten]]
   */
  final def flatten[E, A](io: IO[E, IO[E, A]]): IO[E, A] =
    ZIO.flatten(io)

  /**
   * See [[scalaz.zio.ZIO.foldLeft]]
   */
  final def foldLeft[E, S, A](in: Iterable[A])(zero: S)(f: (S, A) => IO[E, S]): IO[E, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.foreach]]
   */
  final def foreach[E, A, B](in: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachPar]]
   */
  final def foreachPar[E, A, B](as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * See [[scalaz.zio.ZIO.foreachParN]]
   */
  final def foreachParN[E, A, B](n: Long)(as: Iterable[A])(fn: A => IO[E, B]): IO[E, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * See [[scalaz.zio.ZIO.foreach_]]
   */
  final def foreach_[E, A](as: Iterable[A])(f: A => IO[E, _]): IO[E, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[E, A, B](as: Iterable[A])(f: A => IO[E, _]): IO[E, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[E, A, B](n: Long)(as: Iterable[A])(f: A => IO[E, _]): IO[E, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * See [[scalaz.zio.ZIO.forkAll]]
   */
  final def forkAll[E, A](as: Iterable[IO[E, A]]): UIO[Fiber[E, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[scalaz.zio.ZIO.forkAll_]]
   */
  final def forkAll_[E, A](as: Iterable[IO[E, A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * See [[scalaz.zio.ZIO.fromEither]]
   */
  final def fromEither[E, A](v: => Either[E, A]): IO[E, A] =
    ZIO.fromEither(v)

  /**
   * See [[scalaz.zio.ZIO.fromFiber]]
   */
  final def fromFiber[E, A](fiber: => Fiber[E, A]): IO[E, A] =
    ZIO.fromFiber(fiber)

  /**
   * See [[scalaz.zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[E, A](fiber: IO[E, Fiber[E, A]]): IO[E, A] =
    ZIO.fromFiberM(fiber)

  /**
   * See [[scalaz.zio.ZIO.fromFunction]]
   */
  final def fromFunction[A](f: Any => A): UIO[A] =
    ZIO.fromFunction(f)

  /**
   * See [[scalaz.zio.ZIO.fromFuture]]
   */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * See [[scalaz.zio.ZIO.fromOption]]
   */
  final def fromOption[A](v: => Option[A]): IO[Unit, A] = ZIO.fromOption(v)

  /**
   * See [[scalaz.zio.ZIO.fromTry]]
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * See [[scalaz.zio.ZIO.halt]]
   */
  final def halt[E](cause: Cause[E]): IO[E, Nothing] = ZIO.halt(cause)

  /**
   * See See [[scalaz.zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt


  /**
   * See [[scalaz.zio.ZIO.interruptible]]
   */
  final def interruptible[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.interruptible(io)

  /**
   * See [[scalaz.zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.lock]]
   */
  final def lock[E, A](executor: Executor)(io: IO[E, A]): IO[E, A] =
    ZIO.lock(executor)(io)

  /**
   * See [[scalaz.zio.ZIO.mergeAll]]
   */
  final def mergeAll[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[E, A, B](in: Iterable[IO[E, A]])(zero: B)(f: (B, A) => B): IO[E, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * See [[scalaz.zio.ZIO.raceAll]]
   */
  final def raceAll[E, A](io: IO[E, A], ios: Iterable[IO[E, A]]): IO[E, A] = ZIO.raceAll(io, ios)

  /**
   * See [[scalaz.zio.ZIO.reduceAll]]
   */
  final def reduceAll[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[E, A](a: IO[E, A], as: Iterable[IO[E, A]])(f: (A, A) => A): IO[E, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.require]]
   */
  final def require[E, A](error: E): IO[E, Option[A]] => IO[E, A] =
    ZIO.require[E, A](error)

  /**
   * See [[scalaz.zio.ZIO.reserve]]
   */
  def reserve[E, A, B](reservation: IO[E, Reservation[Any, E, A]])(use: A => IO[E, B]): IO[E, B] =
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
   * See [[scalaz.zio.ZIO.interruptChildren]]
   */
  final def interruptChildren[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.interruptChildren(io)

  /**
   * See [[scalaz.zio.ZIO.supervised]]
   */
  def supervised[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.supervised(io)

  /**
   * See [[scalaz.zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[E, A](io: IO[E, A])(supervisor: IndexedSeq[Fiber[_, _]] => UIO[_]): IO[E, A] =
    ZIO.handleChildrenWith(io)(supervisor)

  /**
   * See [[scalaz.zio.ZIO.suspend]]
   */
  final def suspend[E, A](io: => IO[E, A]): IO[E, A] =
    ZIO.suspend(io)

  /**
   * [[scalaz.zio.ZIO.suspendWith]]
   */
  final def suspendWith[E, A](io: Platform => IO[E, A]): IO[E, A] =
    new ZIO.SuspendWith(io)

  /**
   * See [[scalaz.zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * See [[scalaz.zio.ZIO.traced]]
   */
  final def traced[E , A](zio: IO[E, A]): IO[E, A] = ZIO.traced(zio)

  /**
   * See [[scalaz.zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * See [[scalaz.zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[E, A](io: IO[E, A]): IO[E, A] =
    ZIO.uninterruptible(io)

  /**
   * See [[scalaz.zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[E, A](k: ZIO.InterruptStatusRestore => IO[E, A]): IO[E, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.unsandbox]]
   */
  final def unsandbox[E, A](v: IO[Cause[E], A]): IO[E, A] = ZIO.unsandbox(v)

  /**
   * See [[scalaz.zio.ZIO.untraced]]
   */
  final def untraced[E , A](zio: IO[E, A]): IO[E, A] = ZIO.untraced(zio)

  /**
   * See [[scalaz.zio.ZIO.when]]
   */
  final def when[E](b: Boolean)(io: IO[E, _]): IO[E, Unit] =
    ZIO.when(b)(io)

  /**
   * See [[scalaz.zio.ZIO.whenM]]
   */
  final def whenM[E](b: IO[E, Boolean])(io: IO[E, _]): IO[E, Unit] =
    ZIO.whenM(b)(io)

  /**
   * See [[scalaz.zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow


  final class BracketAcquire_[E](private val acquire: IO[E, _]) extends AnyVal {
    def apply(release: IO[Nothing, _]): BracketRelease_[E] =
      new BracketRelease_(acquire, release)
  }
  final class BracketRelease_[E](acquire: IO[E, _], release: IO[Nothing, _]) {
    def apply[E1 >: E, B](use: IO[E1, B]): IO[E1, B] =
      ZIO.bracket(acquire, (_: Any) => release, (_: Any) => use)
  }

  final class BracketAcquire[E, A](private val acquire: IO[E, A]) extends AnyVal {
    def apply(release: A => IO[Nothing, _]): BracketRelease[E, A] =
      new BracketRelease[E, A](acquire, release)
  }
  class BracketRelease[E, A](acquire: IO[E, A], release: A => IO[Nothing, _]) {
    def apply[E1 >: E, B](use: A => IO[E1, B]): IO[E1, B] =
      ZIO.bracket(acquire, release, use)
  }

}
