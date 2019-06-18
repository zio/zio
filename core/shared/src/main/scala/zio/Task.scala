package zio

import zio.Exit.Cause
import zio.internal.{ Executor, Platform }

import scala.concurrent.ExecutionContext

object Task {

  /**
   * See [[zio.ZIO.absolve]]
   */
  final def absolve[A](v: Task[Either[Throwable, A]]): Task[A] =
    ZIO.absolve(v)

  /**
   * See [[zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt
  
  /**
   * See [[zio.ZIO.apply]] 
   */
  def apply[A](a: => A): Task[A] = ZIO.apply(a)

  /**
   * See [[zio.ZIO.bracket]]
   */
  final def bracket[A](acquire: Task[A]): ZIO.BracketAcquire[Any, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * See [[zio.ZIO.bracket]]
   */
  final def bracket[A, B](acquire: Task[A], release: A => UIO[_], use: A => Task[B]): Task[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * See [[zio.ZIO.bracketExit]]
   */
  final def bracketExit[A](acquire: Task[A]): ZIO.BracketExitAcquire[Any, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[zio.ZIO.bracketExit]]
   */
  final def bracketExit[A, B](
    acquire: Task[A],
    release: (A, Exit[Throwable, B]) => UIO[_],
    use: A => Task[B]
  ): Task[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[A](f: InterruptStatus => Task[A]): Task[A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[zio.ZIO.collectAll]]
   */
  final def collectAll[A](in: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[A](as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[A](n: Long)(as: Iterable[Task[A]]): Task[List[A]] =
    ZIO.collectAllParN(n)(as)

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
  final def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[A](f: Fiber.Descriptor => Task[A]): Task[A] =
    ZIO.descriptorWith(f)

  /**
   * See [[zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[A](register: (Task[A] => Unit) => Unit): Task[A] =
    ZIO.effectAsync(register)

  /**
   * See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (Task[A] => Unit) => Option[Task[A]]): Task[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[A](register: (Task[A] => Unit) => UIO[_]): Task[A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[A](register: (Task[A] => Unit) => Either[Canceler, Task[A]]): Task[A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * See [[zio.ZIO.fail]]
   */
  final def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * See [[zio.ZIO.flatten]]
   */
  final def flatten[A](task: Task[Task[A]]): Task[A] =
    ZIO.flatten(task)

  /**
   * See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[S, A](in: Iterable[A])(zero: S)(f: (S, A) => Task[S]): Task[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[zio.ZIO.foreach]]
   */
  final def foreach[A, B](in: Iterable[A])(f: A => Task[B]): Task[List[B]] =
    ZIO.foreach(in)(f)

  /**
   * See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[A, B](as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[A, B](n: Long)(as: Iterable[A])(fn: A => Task[B]): Task[List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * See [[zio.ZIO.foreach_]]
   */
  final def foreach_[A](as: Iterable[A])(f: A => Task[_]): Task[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[A, B](as: Iterable[A])(f: A => Task[_]): Task[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[A, B](n: Long)(as: Iterable[A])(f: A => Task[_]): Task[Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * See [[zio.ZIO.forkAll]]
   */
  final def forkAll[A](as: Iterable[Task[A]]): UIO[Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[A](as: Iterable[Task[A]]): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * See [[zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Throwable, A]): Task[A] =
    ZIO.fromEither(v)

  /**
   * See [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Throwable, A]): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * See [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: Task[Fiber[Throwable, A]]): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * See [[zio.ZIO.fromFuture]]
   */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * See [[zio.ZIO.fromTry]]
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * See [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Throwable]): Task[Nothing] = ZIO.halt(cause)

  /**
   * See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
  * See [[zio.ZIO.interruptible]]
   */
  final def interruptible[A](task: Task[A]): Task[A] =
    ZIO.interruptible(task)

  /**
   * See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[zio.ZIO.lock]]
   */
  final def lock[A](executor: Executor)(task: Task[A]): Task[A] =
    ZIO.lock(executor)(task)

  /**
   * See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[A, B](in: Iterable[Task[A]])(zero: B)(f: (B, A) => B): Task[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[A, B](in: Iterable[Task[A]])(zero: B)(f: (B, A) => B): Task[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * See [[zio.ZIO.raceAll]]
   */
  final def raceAll[A](task: Task[A], ios: Iterable[Task[A]]): Task[A] =
    ZIO.raceAll(task, ios)

  /**
   * See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[A](a: Task[A], as: Iterable[Task[A]])(f: (A, A) => A): Task[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[A](a: Task[A], as: Iterable[Task[A]])(f: (A, A) => A): Task[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[zio.ZIO.require]]
   */
  final def require[A](error: Throwable): Task[Option[A]] => Task[A] =
    ZIO.require[Throwable, A](error)

  /**
   * See [[zio.ZIO.reserve]]
   */
  def reserve[A, B](reservation: Task[Reservation[Any, Throwable, A]])(use: A => Task[B]): Task[B] =
    ZIO.reserve(reservation)(use)

  /**
   * See [[zio.ZIO.runtime]]
   */
  final def runtime: UIO[Runtime[Any]] = ZIO.runtime

  /**
   * See [[zio.ZIO.succeed]]
   */
  final def succeed[A](a: A): UIO[A] = ZIO.succeed(a)

  /**
   * See [[zio.ZIO.succeedLazy]]
   */
  final def succeedLazy[A](a: => A): UIO[A] = ZIO.succeedLazy(a)

  /**
   * See [[zio.ZIO.supervised]]
   */
  final def supervised[A](task: Task[A]): Task[A] =
    ZIO.supervised(task)

  /**
   * See [[zio.ZIO.interruptChildren]]
   */
  final def interruptChildren[A](task: Task[A]): Task[A] =
    ZIO.interruptChildren(task)

  /**
   * See [[zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[A](task: Task[A])(supervisor: IndexedSeq[Fiber[_, _]] => UIO[_]): Task[A] =
    ZIO.handleChildrenWith(task)(supervisor)

  /**
   * See [[zio.ZIO.suspend]]
   */
  final def suspend[A](io: => Task[A]): Task[A] =
    ZIO.suspend(io)

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
  final def traced[A](zio: Task[A]): Task[A] = ZIO.traced(zio)

  /**
   * See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * See [[zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[A](task: Task[A]): Task[A] =
    ZIO.uninterruptible(task)

  /**
   * See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A]): Task[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[zio.ZIO.untraced]]
   */
  final def untraced[A](zio: Task[A]): Task[A] = ZIO.untraced(zio)

  /**
   * See [[zio.ZIO.when]]
   */
  final def when(b: Boolean)(task: Task[_]): Task[Unit] =
    ZIO.when(b)(task)

  /**
   * See [[zio.ZIO.whenM]]
   */
  final def whenM(b: Task[Boolean])(task: Task[_]): Task[Unit] =
    ZIO.whenM(b)(task)

  /**
   * See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow
}
