package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Executor, Platform }

import scala.concurrent.ExecutionContext

object TaskR {

  /**
   * See [[zio.ZIO.absolve]]
   */
  final def absolve[R, A](v: TaskR[R, Either[Throwable, A]]): TaskR[R, A] =
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
   * See [[zio.ZIO.access]]
   */
  final def access[R]: ZIO.AccessPartiallyApplied[R] =
    ZIO.access

  /**
   * See [[zio.ZIO.accessM]]
   */
  final def accessM[R]: ZIO.AccessMPartiallyApplied[R] =
    ZIO.accessM

  /**
   * See [[zio.ZIO.bracket[R, E, A](acquire: zio.ZIO[R,E,A]): zio.ZIO.BracketAcquire[R,E,A]]]
   */
  final def bracket[R, A](acquire: TaskR[R, A]): ZIO.BracketAcquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * See [[zio.ZIO.bracket[R, E, A, B](acquire: zio.ZIO[R,E,A],release: A => zio.ZIO[R, Nothing, _],use: A => zio.ZIO[R,E,B]): zio.ZIO[R,E,B]]]
   */
  final def bracket[R, A, B](
    acquire: TaskR[R, A],
    release: A => ZIO[R, Nothing, _],
    use: A => TaskR[R, B]
  ): TaskR[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * See [[zio.ZIO.bracketExit[R, E, A](acquire: zio.ZIO[R,E,A]): zio.ZIO.BracketAcquire[R,E,A]]]
   */
  final def bracketExit[R, A](acquire: TaskR[R, A]): ZIO.BracketExitAcquire[R, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * See [[zio.ZIO.bracketExit[R, E, A, B](acquire: zio.ZIO[R,E,A],release: (A, zio.Exit[E,B]) => zio.ZIO[R, Nothing, _],use: A => zio.ZIO[R,E,B]): zio.ZIO[R,E,B]]]
   */
  final def bracketExit[R, A, B](
    acquire: TaskR[R, A],
    release: (A, Exit[Throwable, B]) => ZIO[R, Nothing, _],
    use: A => TaskR[R, B]
  ): TaskR[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[R, A](f: InterruptStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * See [[zio.ZIO.checkSupervised]]
   */
  final def checkSupervised[R, A](f: SuperviseStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkSupervised(f)

  /**
   * See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[R, A](f: TracingStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkTraced(f)

  /**
   * See [[zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * See [[zio.ZIO.collectAll]]
   */
  final def collectAll[R, A](in: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[R, A](as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[R, A](n: Long)(as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[R, A](f: Fiber.Descriptor => TaskR[R, A]): TaskR[R, A] =
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
  final def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * See [[zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[R, A](register: (TaskR[R, A] => Unit) => Unit): TaskR[R, A] =
    ZIO.effectAsync(register)

  /**
   * See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (Task[A] => Unit) => Option[Task[A]]): Task[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[R, A](register: (TaskR[R, A] => Unit) => ZIO[R, Nothing, _]): TaskR[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[R, A](register: (TaskR[R, A] => Unit) => Either[Canceler, TaskR[R, A]]): TaskR[R, A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * See [[zio.ZIO.environment]]
   */
  final def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * See [[zio.ZIO.fail]]
   */
  final def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * See [[zio.ZIO.firstSuccessOf]]
   */
  final def firstSuccessOf[R, A](
    taskR: TaskR[R, A],
    rest: Iterable[TaskR[R, A]]
  ): TaskR[R, A] = ZIO.firstSuccessOf(taskR, rest)

  /**
   * See [[zio.ZIO.flatten]]
   */
  final def flatten[R, A](taskr: TaskR[R, TaskR[R, A]]): TaskR[R, A] =
    ZIO.flatten(taskr)

  /**
   * See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => TaskR[R, S]): TaskR[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * See [[zio.ZIO.foreach]]
   */
  final def foreach[R, A, B](in: Iterable[A])(f: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[R, A, B](as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[R, A, B](n: Long)(as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * See [[zio.ZIO.foreach_]]
   */
  final def foreach_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[R, A, B](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[R, A, B](n: Long)(as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * See [[zio.ZIO.forkAll]]
   */
  final def forkAll[R, A](as: Iterable[TaskR[R, A]]): ZIO[R, Nothing, Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[R, A](as: Iterable[TaskR[R, A]]): ZIO[R, Nothing, Unit] =
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
   * See [[zio.ZIO.fromFunction]]
   */
  final def fromFunction[R, A](f: R => A): ZIO[R, Nothing, A] =
    ZIO.fromFunction(f)

  /**
   * See [[zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[R, A](f: R => Task[A]): TaskR[R, A] =
    ZIO.fromFunctionM(f)

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
   * See [[zio.ZIO.haltWith]]
   */
  final def haltWith[R](function: (() => ZTrace) => Cause[Throwable]): TaskR[R, Nothing] =
    ZIO.haltWith(function)

  /**
   * See [[zio.ZIO.identity]]
   */
  final def identity[R]: ZIO[R, Nothing, R] = ZIO.identity

  /**
   * See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * See [[zio.ZIO.interruptible]]
   */
  final def interruptible[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptible(taskr)

  /**
   * See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * See [[zio.ZIO.lock]]
   */
  final def lock[R, A](executor: Executor)(taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   * See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[R, A, B](in: Iterable[TaskR[R, A]])(zero: B)(f: (B, A) => B): TaskR[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[R, A, B](in: Iterable[TaskR[R, A]])(zero: B)(f: (B, A) => B): TaskR[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * See [[zio.ZIO.provide]]
   */
  final def provide[R, A](r: R): TaskR[R, A] => Task[A] =
    ZIO.provide(r)

  /**
   * See [[zio.ZIO.raceAll]]
   */
  final def raceAll[R, R1 <: R, A](taskr: TaskR[R, A], taskrs: Iterable[TaskR[R1, A]]): TaskR[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[R, R1 <: R, A](a: TaskR[R, A], as: Iterable[TaskR[R1, A]])(f: (A, A) => A): TaskR[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[R, R1 <: R, A](a: TaskR[R, A], as: Iterable[TaskR[R1, A]])(f: (A, A) => A): TaskR[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * See [[zio.ZIO.require]]
   */
  final def require[R, A](error: Throwable): IO[Throwable, Option[A]] => IO[Throwable, A] =
    ZIO.require(error)

  /**
   * See [[zio.ZIO.reserve]]
   */
  final def reserve[R, A, B](reservation: TaskR[R, Reservation[R, Throwable, A]])(use: A => TaskR[R, B]): TaskR[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   * See [[zio.ZIO.runtime]]
   */
  final def runtime[R]: ZIO[R, Nothing, Runtime[R]] = ZIO.runtime

  /**
   * See [[zio.ZIO.sleep]]
   */
  final def sleep(duration: Duration): TaskR[Clock, Unit] =
    ZIO.sleep(duration)

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
  final def interruptChildren[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptChildren(taskr)

  /**
   * See [[zio.ZIO.handleChildrenWith]]
   */
  final def handleChildrenWith[R, A](
    taskr: TaskR[R, A]
  )(supervisor: IndexedSeq[Fiber[_, _]] => ZIO[R, Nothing, _]): TaskR[R, A] =
    ZIO.handleChildrenWith(taskr)(supervisor)

  /**
   *  See [[zio.ZIO.sequence]]
   */
  final def sequence[R, A](in: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.sequence(in)

  /**
   *  See [[zio.ZIO.sequencePar]]
   */
  final def sequencePar[R, A](as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.sequencePar(as)

  /**
   *  See [[zio.ZIO.sequenceParN]]
   */
  final def sequenceParN[R, A](n: Long)(as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.sequenceParN(n)(as)

  /**
   * See [[zio.ZIO.supervised]]
   */
  final def supervised[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.supervised(taskr)

  /**
   * See [[zio.ZIO.superviseStatus]]
   */
  final def superviseStatus[R, A](status: SuperviseStatus)(taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.superviseStatus(status)(taskr)

  /**
   * See [[zio.ZIO.suspend]]
   */
  final def suspend[R, A](taskr: => TaskR[R, A]): TaskR[R, A] =
    ZIO.suspend(taskr)

  /**
   * [[zio.ZIO.suspendWith]]
   */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

  /**
   * See [[zio.ZIO.swap]]
   */
  final def swap[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, (B, A)] =
    ZIO.swap

  /**
   * See [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * See [[zio.ZIO.traced]]
   */
  final def traced[R, A](zio: TaskR[R, A]): TaskR[R, A] = ZIO.traced(zio)

  /**
   * See [[zio.ZIO.traverse]]
   */
  final def traverse[R, A, B](in: Iterable[A])(f: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.traverse(in)(f)

  /**
   * See [[zio.ZIO.traversePar]]
   */
  final def traversePar[R, A, B](as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.traversePar(as)(fn)

  /**
   * Alias for [[ZIO.foreachParN]]
   */
  final def traverseParN[R, A, B](
    n: Long
  )(as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.traverseParN(n)(as)(fn)

  /**
   * See [[zio.ZIO.traverse_]]
   */
  final def traverse_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.traverse_(as)(f)

  /**
   * See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[R, A](
    n: Long
  )(as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * See [[zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => TaskR[R, A]): TaskR[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * See [[zio.ZIO.unsandbox]]
   */
  final def unsandbox[R, A](v: IO[Cause[Throwable], A]): TaskR[R, A] = ZIO.unsandbox(v)

  /**
   * See [[zio.ZIO.unsupervised]]
   */
  final def unsupervised[R, A](taskR: TaskR[R, A]): TaskR[R, A] =
    ZIO.unsupervised(taskR)

  /**
   * See [[zio.ZIO.untraced]]
   */
  final def untraced[R, A](zio: TaskR[R, A]): TaskR[R, A] = ZIO.untraced(zio)

  /**
   * See [[zio.ZIO.when]]
   */
  final def when[R](b: Boolean)(taskR: TaskR[R, _]): TaskR[R, Unit] =
    ZIO.when(b)(taskR)

  /**
   * See [[zio.ZIO.whenM]]
   */
  final def whenM[R](b: TaskR[R, Boolean])(taskR: TaskR[R, _]): TaskR[R, Unit] =
    ZIO.whenM(b)(taskR)

  /**
   * See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * See [[zio.ZIO._1]]
   */
  final def _1[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, A] = ZIO._1

  /**
   * See [[zio.ZIO._2]]
   */
  final def _2[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, B] = ZIO._2

}
