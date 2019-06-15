package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.Exit.Cause
import zio.internal.{Executor, Platform}

import scala.concurrent.ExecutionContext

object TaskR {

  /**
   * See [[scalaz.zio.ZIO.absolve]]
   */
  final def absolve[R, A](v: TaskR[R, Either[Throwable, A]]): TaskR[R, A] =
    ZIO.absolve(v)

  /**
   * See [[scalaz.zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  def apply[A](a: => A): Task[A] = effect(a)

  /**
   * See [[scalaz.zio.ZIO.access]]
   */
  final def access[R]: ZIO.AccessPartiallyApplied[R] =
    ZIO.access

  /**
   * See [[scalaz.zio.ZIO.accessM]]
   */
  final def accessM[R]: ZIO.AccessMPartiallyApplied[R] =
    ZIO.accessM

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[R, A](acquire: TaskR[R, A]): ZIO.BracketAcquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracket]]
   */
  final def bracket[R, A, B](acquire: TaskR[R, A],
                             release: A => ZIO[R, Nothing, _],
                             use:     A => TaskR[R, B]): TaskR[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[R, A](acquire: TaskR[R, A]): ZIO.BracketExitAcquire[R, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[scalaz.zio.ZIO.bracketExit]]
   */
  final def bracketExit[R, A, B](acquire: TaskR[R, A],
                                 release: (A, Exit[Throwable, B]) => ZIO[R, Nothing, _],
                                 use:     A => TaskR[R, B]): TaskR[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[scalaz.zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[R, A](f: InterruptStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[scalaz.zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[scalaz.zio.ZIO.collectAll]]
   */
  final def collectAll[R, A](in: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[scalaz.zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[R, A](as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[scalaz.zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[R, A](n: Long)(as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * See [[scalaz.zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[scalaz.zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[R, A](f: Fiber.Descriptor => TaskR[R, A]): TaskR[R, A] =
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
  final def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * See [[scalaz.zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * See [[scalaz.zio.ZIO.effectAsync]]
   */
  final def effectAsync[R, A](register: (TaskR[R, A] => Unit) => Unit): TaskR[R, A] =
    ZIO.effectAsync(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (Task[A] => Unit) => Option[Task[A]]): Task[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[R, A](register: (TaskR[R, A] => Unit) => ZIO[R, Nothing, _]): TaskR[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[scalaz.zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[R, A](register: (TaskR[R, A] => Unit) => Either[Canceler, TaskR[R, A]]): TaskR[R, A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[scalaz.zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * See [[scalaz.zio.ZIO.environment]]
   */
  final def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * See [[scalaz.zio.ZIO.fail]]
   */
  final def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * See [[scalaz.zio.ZIO.flatten]]
   */
  final def flatten[R, A](taskr: TaskR[R, TaskR[R, A]]): TaskR[R, A] =
    ZIO.flatten(taskr)

  /**
   * See [[scalaz.zio.ZIO.foldLeft]]
   */
  final def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => TaskR[R, S]): TaskR[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.foreach]]
   */
  final def foreach[R, A, B](in: Iterable[A])(f: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachPar]]
   */
  final def foreachPar[R, A, B](as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * See [[scalaz.zio.ZIO.foreachParN]]
   */
  final def foreachParN[R, A, B](n: Long)(as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * See [[scalaz.zio.ZIO.foreach_]]
   */
  final def foreach_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[R, A, B](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * See [[scalaz.zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[R, A, B](n: Long)(as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * See [[scalaz.zio.ZIO.forkAll]]
   */
  final def forkAll[R, A](as: Iterable[TaskR[R, A]]): ZIO[R, Nothing, Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[scalaz.zio.ZIO.forkAll_]]
   */
  final def forkAll_[R, A](as: Iterable[TaskR[R, A]]): ZIO[R, Nothing, Unit] =
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
   * See [[scalaz.zio.ZIO.fromFunction]]
   */
  final def fromFunction[R, A](f: R => A): ZIO[R, Nothing, A] =
    ZIO.fromFunction(f)

  /**
   * See [[scalaz.zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[R, A](f: R => Task[A]): TaskR[R, A] =
    ZIO.fromFunctionM(f)

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
   * See [[scalaz.zio.ZIO.identity]]
   */
  final def identity[R]: ZIO[R, Nothing, R] = ZIO.identity

  /**
   * See [[scalaz.zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * See [[scalaz.zio.ZIO.interruptible]]
   */
  final def interruptible[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptible(taskr)

  /**
   * See [[scalaz.zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.lock]]
   */
  final def lock[R, A](executor: Executor)(taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   * See [[scalaz.zio.ZIO.mergeAll]]
   */
  final def mergeAll[R, A, B](in: Iterable[TaskR[R, A]])(zero: B)(f: (B, A) => B): TaskR[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[R, A, B](in: Iterable[TaskR[R, A]])(zero: B)(f: (B, A) => B): TaskR[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[scalaz.zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * See [[scalaz.zio.ZIO.provide]]
   */
  final def provide[R, A](r: R): TaskR[R, A] => Task[A] =
    ZIO.provide(r)

  /**
   * See [[scalaz.zio.ZIO.raceAll]]
   */
  final def raceAll[R, R1 <: R, A](taskr: TaskR[R, A], taskrs: Iterable[TaskR[R1, A]]): TaskR[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * See [[scalaz.zio.ZIO.reduceAll]]
   */
  final def reduceAll[R, R1 <: R, A](a: TaskR[R, A], as: Iterable[TaskR[R1, A]])(f: (A, A) => A): TaskR[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[R, R1 <: R, A](a: TaskR[R, A], as: Iterable[TaskR[R1, A]])(f: (A, A) => A): TaskR[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[scalaz.zio.ZIO.require]]
   */
  final def require[R, A](error: Throwable): IO[Throwable, Option[A]] => IO[Throwable, A] =
    ZIO.require(error)

  /**
   * See [[scalaz.zio.ZIO.reserve]]
   */
  def reserve[R, A, B](reservation: TaskR[R, Reservation[R, Throwable, A]])(use: A => TaskR[R, B]): TaskR[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   * See [[scalaz.zio.ZIO.runtime]]
   */
  final def runtime[R]: ZIO[R, Nothing, Runtime[R]] = ZIO.runtime

  /**
   * See [[scalaz.zio.ZIO.sleep]]
   */
  final def sleep(duration: Duration): TaskR[Clock, Unit] =
    ZIO.sleep(duration)

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
  final def interruptChildren[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptChildren(taskr)
  
  /**
   * See [[scalaz.zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[R, A](taskr: TaskR[R, A])(supervisor: IndexedSeq[Fiber[_, _]] => ZIO[R, Nothing, _]): TaskR[R, A] =
    ZIO.handleChildrenWith(taskr)(supervisor)

  /**
   * See [[scalaz.zio.ZIO.supervised]]
   */
  final def supervised[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.supervised(taskr)

  /**
   * See [[scalaz.zio.ZIO.suspend]]
   */
  final def suspend[R, A](taskr: => TaskR[R, A]): TaskR[R, A] =
    ZIO.suspend(taskr)

  /**
   * [[scalaz.zio.ZIO.suspendWith]]
   */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

  /**
   * See [[scalaz.zio.ZIO.swap]]
   */
  final def swap[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, (B, A)] =
    ZIO.swap

  /**
   * See [[scalaz.zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * See [[scalaz.zio.ZIO.traced]]
   */
  final def traced[R, A](zio: TaskR[R, A]): TaskR[R, A] = ZIO.traced(zio)

  /**
   * See [[scalaz.zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * See [[scalaz.zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * See [[scalaz.zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => TaskR[R, A]): TaskR[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[scalaz.zio.ZIO.untraced]]
   */
  final def untraced[R, A](zio: TaskR[R, A]): TaskR[R, A] = ZIO.untraced(zio)

  /**
   * See [[scalaz.zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * See [[scalaz.zio.ZIO._1]]
   */
  final def _1[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, A] = ZIO._1

  /**
   * See [[scalaz.zio.ZIO._2]]
   */
  final def _2[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, B] = ZIO._2

}
