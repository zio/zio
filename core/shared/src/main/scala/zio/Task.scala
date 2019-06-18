package scalaz.zio

import scalaz.zio.Exit.Cause
import scalaz.zio.internal.{ Executor, Platform }

import scala.concurrent.ExecutionContext

object Task {

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
  final def absolve[A](v: Task[Either[Throwable, A]]): Task[A] =
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
  final def bracket[A](acquire: Task[A]): ZIO.BracketAcquire[Any, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[A, B](acquire: Task[A], release: A => UIO[_], use: A => Task[B]): Task[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[A](acquire: Task[A]): ZIO.BracketExitAcquire[Any, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[A, B](
    acquire: Task[A],
    release: (A, Exit[Throwable, B]) => UIO[_],
    use: A => Task[B]
  ): Task[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[A](f: Boolean => Task[A]): Task[A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[scalaz.zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[scalaz.zio.ZIO.collectAll]]
   */
  final def collectAll[A](in: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[scalaz.zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[A](as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[scalaz.zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[A](n: Long)(as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllParN(n)(as)

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
  final def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * See [[scalaz.zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[scalaz.zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[A](f: Fiber.Descriptor => Task[A]): Task[A] =
    ZIO.descriptorWith(f)

  /**
   * See [[scalaz.zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * See [[scalaz.zio.ZIO.effectAsync]]
   */
  final def effectAsync[A](register: (Task[A] => Unit) => Unit): Task[A] =
    ZIO.effectAsync(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (Task[A] => Unit) => Option[Task[A]]): Task[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[A](register: (Task[A] => Unit) => UIO[_]): Task[A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[A](register: (Task[A] => Unit) => Either[Canceler, Task[A]]): Task[A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[scalaz.zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * See [[scalaz.zio.ZIO.fail]]
   */
  final def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * See [[scalaz.zio.ZIO.flatten]]
   */
  final def flatten[A](task: Task[Task[A]]): Task[A] =
    ZIO.flatten(task)

  /**
   * See [[scalaz.zio.ZIO.foldLeft]]
   */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => Task[S]): Task[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.foreach]]
   */
  final def foreach[A, B](in: Iterable[A])(f: A => Task[B]): Task[List[B]] =
    ZIO.foreach(in)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachPar]]
   */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * See [[scalaz.zio.ZIO.foreachParN]]
   */
  final def foreachParN[A, B](n: Long)(as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * See [[scalaz.zio.ZIO.foreach_]]
   */
  final def foreach_[A](as: Iterable[A])(f: A => Task[_]): Task[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[A, B](as: Iterable[A])(f: A => Task[_]): Task[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[A, B](n: Long)(as: Iterable[A])(f: A => Task[_]): Task[Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * See [[scalaz.zio.ZIO.forkAll]]
   */
  final def forkAll[A](as: Iterable[Task[A]]): UIO[Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[scalaz.zio.ZIO.forkAll_]]
   */
  final def forkAll_[A](as: Iterable[Task[A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * See [[scalaz.zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Throwable, A]): Task[A] =
    ZIO.fromEither(v)

  /**
   * See [[scalaz.zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Throwable, A]): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * See [[scalaz.zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: Task[Fiber[Throwable, A]]): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * See [[scalaz.zio.ZIO.fromFuture]]
   */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * See [[scalaz.zio.ZIO.fromTry]]
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * See [[scalaz.zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Throwable]): Task[Nothing] = ZIO.halt(cause)

  /**
   * See [[scalaz.zio.ZIO.interruptible]]
   */
  final def interruptible[A](task: Task[A]): Task[A] =
    ZIO.interruptible(task)

  /**
   * See [[scalaz.zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.lock]]
   */
  final def lock[A](executor: Executor)(task: Task[A]): Task[A] =
    ZIO.lock(executor)(task)

  /**
   * See [[scalaz.zio.ZIO.mergeAll]]
   */
  final def mergeAll[A, B](in: Iterable[Task[A]])(zero: B)(f: (B, A) => B): Task[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[A, B](in: Iterable[Task[A]])(zero: B)(f: (B, A) => B): Task[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.raceAll]]
   */
  final def raceAll[A](task: Task[A], ios: Iterable[Task[A]]): Task[A] =
    ZIO.raceAll(task, ios)

  /**
   * See [[scalaz.zio.ZIO.reduceAll]]
   */
  final def reduceAll[A](a: Task[A], as: Iterable[Task[A]])(f: (A, A) => A): Task[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[A](a: Task[A], as: Iterable[Task[A]])(f: (A, A) => A): Task[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.require]]
   */
  final def require[A](error: Throwable): Task[Option[A]] => Task[A] =
    ZIO.require[Any, Throwable, A](error)

  /**
   * See [[scalaz.zio.ZIO.reserve]]
   */
  def reserve[A, B](reservation: Task[Reservation[Any, Throwable, A]])(use: A => Task[B]): Task[B] =
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
   * See [[scalaz.zio.ZIO.supervised]]
   */
  final def supervised[A](task: Task[A]): Task[A] =
    ZIO.supervised(task)

  /**
   * See [[scalaz.zio.ZIO.supervise]]
   */
  final def supervise[A](task: Task[A]): Task[A] =
    ZIO.supervise(task)

  /**
   * See [[scalaz.zio.ZIO.superviseWith]]
   */
  final def superviseWith[A](task: Task[A])(supervisor: IndexedSeq[Fiber[_, _]] => UIO[_]): Task[A] =
    ZIO.superviseWith(task)(supervisor)

  /**
   * See [[scalaz.zio.ZIO.suspend]]
   */
  final def suspend[A](io: => Task[A]): Task[A] =
    ZIO.suspend(io)

  /**
    * [[scalaz.zio.ZIO.suspendWith]]
    */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

  /**
   * See [[scalaz.zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[A](task: Task[A]): Task[A] =
    ZIO.uninterruptible(task)

  /**
   * See [[scalaz.zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.when]]
   */
  final def when(b: Boolean)(task: Task[_]): Task[Unit] =
    ZIO.when(b)(task)

  /**
   * See [[scalaz.zio.ZIO.whenM]]
   */
  final def whenM(b: Task[Boolean])(task: Task[_]): Task[Unit] =
    ZIO.whenM(b)(task)

}
