package zio

import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Executor, Platform }

import scala.concurrent.ExecutionContext

object TaskR {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  final def absolve[R, A](v: TaskR[R, Either[Throwable, A]]): TaskR[R, A] =
    ZIO.absolve(v)

  /**
   * @see See [[zio.ZIO.allowInterrupt]]
   */
  final def allowInterrupt: UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see See [[zio.ZIO.apply]]
   */
  def apply[A](a: => A): Task[A] = ZIO.apply(a)

  /**
   * @see See [[zio.ZIO.access]]
   */
  final def access[R]: ZIO.AccessPartiallyApplied[R] =
    ZIO.access

  /**
   * @see See [[zio.ZIO.accessM]]
   */
  final def accessM[R]: ZIO.AccessMPartiallyApplied[R] =
    ZIO.accessM

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[R, A](acquire: TaskR[R, A]): ZIO.BracketAcquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  final def bracket[R, A, B](
    acquire: TaskR[R, A],
    release: A => ZIO[R, Nothing, _],
    use: A => TaskR[R, B]
  ): TaskR[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[R, A](acquire: TaskR[R, A]): ZIO.BracketExitAcquire[R, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  final def bracketExit[R, A, B](
    acquire: TaskR[R, A],
    release: (A, Exit[Throwable, B]) => ZIO[R, Nothing, _],
    use: A => TaskR[R, B]
  ): TaskR[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  final def checkInterruptible[R, A](f: InterruptStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkSupervised]]
   */
  final def checkSupervised[R, A](f: SuperviseStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkSupervised(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  final def checkTraced[R, A](f: TracingStatus => TaskR[R, A]): TaskR[R, A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[zio.ZIO.children]]
   */
  final def children: UIO[IndexedSeq[Fiber[_, _]]] = ZIO.children

  /**
   * @see See [[zio.ZIO.collectAll]]
   */
  final def collectAll[R, A](in: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAllPar]]
   */
  final def collectAllPar[R, A](as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  final def collectAllParN[R, A](n: Long)(as: Iterable[TaskR[R, A]]): TaskR[R, List[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  final def descriptor: UIO[Fiber.Descriptor] = ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  final def descriptorWith[R, A](f: Fiber.Descriptor => TaskR[R, A]): TaskR[R, A] =
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
  final def done[A](r: Exit[Throwable, A]): Task[A] = ZIO.done(r)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  final def effect[A](effect: => A): Task[A] = ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  final def effectAsync[R, A](register: (TaskR[R, A] => Unit) => Unit): TaskR[R, A] =
    ZIO.effectAsync(register)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  final def effectAsyncMaybe[A](register: (Task[A] => Unit) => Option[Task[A]]): Task[A] =
    ZIO.effectAsyncMaybe(register)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  final def effectAsyncM[R, A](register: (TaskR[R, A] => Unit) => ZIO[R, Nothing, _]): TaskR[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  final def effectAsyncInterrupt[R, A](register: (TaskR[R, A] => Unit) => Either[Canceler, TaskR[R, A]]): TaskR[R, A] =
    ZIO.effectAsyncInterrupt(register)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  final def effectTotal[A](effect: => A): UIO[A] = ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.environment]]
   */
  final def environment[R]: ZIO[R, Nothing, R] = ZIO.environment

  /**
   * @see See [[zio.ZIO.fail]]
   */
  final def fail(error: Throwable): Task[Nothing] = ZIO.fail(error)

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  final def firstSuccessOf[R, A](
    taskR: TaskR[R, A],
    rest: Iterable[TaskR[R, A]]
  ): TaskR[R, A] = ZIO.firstSuccessOf(taskR, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  final def flatten[R, A](taskr: TaskR[R, TaskR[R, A]]): TaskR[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  final def foldLeft[R, S, A](in: Iterable[A])(zero: S)(f: (S, A) => TaskR[R, S]): TaskR[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foreach]]
   */
  final def foreach[R, A, B](in: Iterable[A])(f: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachPar]]
   */
  final def foreachPar[R, A, B](as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  final def foreachParN[R, A, B](n: Long)(as: Iterable[A])(fn: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  final def foreach_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  final def foreachPar_[R, A, B](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  final def foreachParN_[R, A, B](n: Long)(as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  final def forkAll[R, A](as: Iterable[TaskR[R, A]]): ZIO[R, Nothing, Fiber[Throwable, List[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  final def forkAll_[R, A](as: Iterable[TaskR[R, A]]): ZIO[R, Nothing, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  final def fromEither[A](v: => Either[Throwable, A]): Task[A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  final def fromFiber[A](fiber: => Fiber[Throwable, A]): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  final def fromFiberM[A](fiber: Task[Fiber[Throwable, A]]): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see See [[zio.ZIO.fromFunction]]
   */
  final def fromFunction[R, A](f: R => A): ZIO[R, Nothing, A] =
    ZIO.fromFunction(f)

  /**
   * @see See [[zio.ZIO.fromFunctionM]]
   */
  final def fromFunctionM[R, A](f: R => Task[A]): TaskR[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see See [[zio.ZIO.fromFuture]]
   */
  final def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A]): Task[A] =
    ZIO.fromFuture(make)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  final def fromTry[A](value: => scala.util.Try[A]): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  final def halt(cause: Cause[Throwable]): Task[Nothing] = ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  final def haltWith[R](function: (() => ZTrace) => Cause[Throwable]): TaskR[R, Nothing] =
    ZIO.haltWith(function)

  /**
   * @see See [[zio.ZIO.identity]]
   */
  final def identity[R]: ZIO[R, Nothing, R] = ZIO.identity

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  final val interrupt: UIO[Nothing] = ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  final def interruptible[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  final def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  final def lock[R, A](executor: Executor)(taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  final def left[R, A](a: A): TaskR[R, Either[A, Nothing]] = ZIO.left(a)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  final def mergeAll[R, A, B](in: Iterable[TaskR[R, A]])(zero: B)(f: (B, A) => B): TaskR[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  final def mergeAllPar[R, A, B](in: Iterable[TaskR[R, A]])(zero: B)(f: (B, A) => B): TaskR[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  final val never: UIO[Nothing] = ZIO.never

  /**
   * @see See [[zio.ZIO.none]]
   */
  final val none: UIO[Option[Nothing]] = ZIO.none

  /**
   * @see See [[zio.ZIO.provide]]
   */
  final def provide[R, A](r: R): TaskR[R, A] => Task[A] =
    ZIO.provide(r)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  final def raceAll[R, R1 <: R, A](taskr: TaskR[R, A], taskrs: Iterable[TaskR[R1, A]]): TaskR[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  final def reduceAll[R, R1 <: R, A](a: TaskR[R, A], as: Iterable[TaskR[R1, A]])(f: (A, A) => A): TaskR[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  final def reduceAllPar[R, R1 <: R, A](a: TaskR[R, A], as: Iterable[TaskR[R1, A]])(f: (A, A) => A): TaskR[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.require]]
   */
  final def require[R, A](error: Throwable): IO[Throwable, Option[A]] => IO[Throwable, A] =
    ZIO.require(error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  final def reserve[R, A, B](reservation: TaskR[R, Reservation[R, Throwable, A]])(use: A => TaskR[R, B]): TaskR[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[R, B](b: B): TaskR[R, Either[Nothing, B]] = ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  final def runtime[R]: ZIO[R, Nothing, Runtime[R]] = ZIO.runtime

  /**
   * @see See [[zio.ZIO.sleep]]
   */
  final def sleep(duration: Duration): TaskR[Clock, Unit] =
    ZIO.sleep(duration)

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[R, A](a: A): TaskR[R, Option[A]] = ZIO.some(a)

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
  final def interruptChildren[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.interruptChildren(taskr)

  /**
   * @see See [[zio.ZIO.handleChildrenWith]]
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
   * @see See [[zio.ZIO.supervised]]
   */
  final def supervised[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.supervised(taskr)

  /**
   * @see See [[zio.ZIO.superviseStatus]]
   */
  final def superviseStatus[R, A](status: SuperviseStatus)(taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.superviseStatus(status)(taskr)

  /**
   * @see See [[zio.ZIO.suspend]]
   */
  final def suspend[R, A](taskr: => TaskR[R, A]): TaskR[R, A] =
    ZIO.suspend(taskr)

  /**
   * [[zio.ZIO.suspendWith]]
   */
  final def suspendWith[A](io: Platform => UIO[A]): UIO[A] =
    new ZIO.SuspendWith(io)

  /**
   * @see See [[zio.ZIO.swap]]
   */
  final def swap[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, (B, A)] =
    ZIO.swap

  /**
   * @see See [[zio.ZIO.trace]]
   * */
  final def trace: UIO[ZTrace] = ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  final def traced[R, A](zio: TaskR[R, A]): TaskR[R, A] = ZIO.traced(zio)

  /**
   * @see See [[zio.ZIO.traverse]]
   */
  final def traverse[R, A, B](in: Iterable[A])(f: A => TaskR[R, B]): TaskR[R, List[B]] =
    ZIO.traverse(in)(f)

  /**
   * @see See [[zio.ZIO.traversePar]]
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
   * @see See [[zio.ZIO.traverse_]]
   */
  final def traverse_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.traverse_(as)(f)

  /**
   * @see See [[zio.ZIO.traversePar_]]
   */
  final def traversePar_[R, A](as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.traversePar_(as)(f)

  /**
   * @see See [[zio.ZIO.traverseParN_]]
   */
  final def traverseParN_[R, A](
    n: Long
  )(as: Iterable[A])(f: A => TaskR[R, _]): TaskR[R, Unit] =
    ZIO.traverseParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  final val unit: UIO[Unit] = ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  final def uninterruptible[R, A](taskr: TaskR[R, A]): TaskR[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  final def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => TaskR[R, A]): TaskR[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unsandbox]]
   */
  final def unsandbox[R, A](v: IO[Cause[Throwable], A]): TaskR[R, A] = ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.unsupervised]]
   */
  final def unsupervised[R, A](taskR: TaskR[R, A]): TaskR[R, A] =
    ZIO.unsupervised(taskR)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  final def untraced[R, A](zio: TaskR[R, A]): TaskR[R, A] = ZIO.untraced(zio)

  /**
   * @see See [[zio.ZIO.when]]
   */
  final def when[R](b: Boolean)(taskR: TaskR[R, _]): TaskR[R, Unit] =
    ZIO.when(b)(taskR)

  /**
   * @see See [[zio.ZIO.whenM]]
   */
  final def whenM[R](b: TaskR[R, Boolean])(taskR: TaskR[R, _]): TaskR[R, Unit] =
    ZIO.whenM(b)(taskR)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  final val yieldNow: UIO[Unit] = ZIO.yieldNow

  /**
   * @see See [[zio.ZIO._1]]
   */
  final def _1[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, A] = ZIO._1

  /**
   * @see See [[zio.ZIO._2]]
   */
  final def _2[R, A, B](implicit ev: R <:< (A, B)): TaskR[R, B] = ZIO._2

}
