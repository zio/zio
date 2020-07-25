package zio.stream.experiment1

import zio._

sealed abstract class ZStream1[-R, +E, +I](val process: ZStream1.Process[R, E, I]) {
  self =>

  def forever: ZStream1[R, E, I] =
    ZStream1(for {
      env    <- ZManaged.access[R](identity)
      ref    <- ZRef.makeManaged[Pull[E, I]](Pull.end)
      switch <- ZManaged.switchable[Any, Nothing, Pull[E, I]]
    } yield {
      def go: Pull[E, I] =
        ref.get.flatten.catchAllCause(Pull.recover(switch(process.provide(env)).flatMap(ref.set) *> go))
      go
    })

  def run[R1 <: R, E1 >: E, O](downstream: ZSink1[R1, E1, I, O]): ZIO[R1, E1, O] =
    self >>: downstream

  def runDrain: ZIO[R, E, Unit] =
    self >>: ZSink1.drain

  def take(n: Long): ZStream1[R, E, I] =
    self >>: ZTransducer1.take(n)

  def takeUntil(p: I => Boolean): ZStream1[R, E, I] =
    self >>: ZTransducer1.takeUntil(p)

  def takeWhile(p: I => Boolean): ZStream1[R, E, I] =
    self >>: ZTransducer1.takeWhile(p)
}

object ZStream1 {

  type Process[-R, +E, +I] = URManaged[R, Pull[E, I]]

  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied[R]()

  def apply[R, E, I](process: Process[R, E, I]): ZStream1[R, E, I] =
    new ZStream1(process) {}

  def apply[I](i: I*): ZStream1[Any, Nothing, I] =
    fromIterable(i)

  def fromIterable[I](is: Iterable[I]): ZStream1[Any, Nothing, I] =
    ZStream1(Process.stateful(is)(_.modify(s => if (s.isEmpty) (Pull.end, s) else (Pull.emit(s.head), s.tail)).flatten))

  def fromManaged[R, E, I](z: ZManaged[R, E, I]): ZStream1[R, E, I] =
    ZStream1(
      for {
        env     <- ZManaged.access[R](identity)
        done    <- ZRef.makeManaged(false)
        release <- ZManaged.ReleaseMap.makeManaged(ExecutionStrategy.Sequential)
      } yield ZIO.uninterruptibleMask(restore =>
        ZIO.ifM(done.get)(
          Pull.end,
          restore(z.zio.bimap(Some(_), _._2).provide((env, release))).ensuring(done.set(true))
        )
      )
    )

  def fromPull[E, I](z: Pull[E, I]): ZStream1[Any, E, I] =
    ZStream1(Process.stateful(false)(_.getAndSet(true).flatMap(if (_) Pull.end else z)))

  def repeatPull[E, I](z: Pull[E, I]): ZStream1[Any, E, I] =
    ZStream1(ZManaged.succeedNow(z))

  def repeatPullWith[R, E, I](z: Pull[E, I], s: Schedule[R, I, _]): ZStream1[R, E, I] =
    ZStream1(
      ZManaged
        .access[R](s.provide)
        .flatMap(ss =>
          ss.initial.toManaged_.flatMap(
            Process.stateful(_)(ref => z.tap(i => ref.get.flatMap(ss.update(i, _).foldM(_ => Pull.end, ref.set))))
          )
        )
    )

  def service[A: Tag]: ServicePartiallyApplied[A] =
    new ServicePartiallyApplied[A]()

  def unfold[S, I](init: S)(pull: S => (I, S)): ZStream1[Any, Nothing, I] =
    ZStream1(Process.stateful(init)(_.modify(pull)))

  def unfoldM[S, E, I](init: S)(pull: S => Pull[E, (I, S)]): ZStream1[Any, E, I] =
    ZStream1(
      Process.stateful(init)(ref =>
        ref.get.flatMap(pull).flatMap {
          case (i, s) => ref.set(s).as(i)
        }
      )
    )

  final class AccessPartiallyApplied[R](private val dummy: Boolean = false) {

    def apply[E, I](pull: R => Pull[E, I]): ZStream1[R, E, I] =
      ZStream1(ZManaged.access[R](pull))
  }

  final class ServicePartiallyApplied[A: Tag](private val dummy: Boolean = false) {

    def apply[E, I](pull: A => Pull[E, I]): ZStream1[Has[A], E, I] =
      ZStream1(ZManaged.service[A].map(pull))
  }

  object Process {

    def stateful[S, E, I](init: S)(pull: Ref[S] => Pull[E, I]): Process[Any, E, I] =
      ZRef.makeManaged(init).map(pull)
  }
}
