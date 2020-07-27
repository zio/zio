package zio.stream.experiment2

import zio.{ Ref, Schedule, URManaged, ZIO, ZManaged, ZRef }

abstract class ZStream2[-R, +E, +I](val process: ZStream2.Process[R, E, I]) {
  self =>

  def filter(p: I => Boolean): ZStream2[R, E, I] =
    ZStream2(self.process.map(_.doUntil(p)))

  def forever: ZStream2[R, E, I] =
    ZStream2(for {
      env    <- ZManaged.access[R](identity)
      ref    <- ZRef.makeManaged[Pull[E, I]](Pull.end)
      switch <- ZManaged.switchable[Any, Nothing, Pull[E, I]]
    } yield {
      def go: Pull[E, I] =
        ref.get.flatten.catchAllCause(Pull.recover(switch(process.provide(env)).flatMap(ref.set) *> go))
      go
    })

  def map[II](f: I => II): ZStream2[R, E, II] =
    self >>: ZTransducer2.map(f)

  def run[R1 <: R, E1 >: E, I1 >: I, O](downstream: ZSink2[R1, E1, I1, O]): ZIO[R1, E1, O] =
    downstream.aggregate(self)

  def take(n: Long): ZStream2[R, E, I] =
    ZStream2(for {
      ref  <- ZRef.makeManaged(n)
      pull <- self.process
    } yield ref.modify(rem => if (rem > 0) (pull, rem - 1) else (Pull.end, rem)).flatten)
}

object ZStream2 {

  type Process[-R, +E, +I] = URManaged[R, Pull[E, I]]

  def apply[R, E, I](process: Process[R, E, I]): ZStream2[R, E, I] =
    new ZStream2(process) {}

  def apply[I](i: I*): ZStream2[Any, Nothing, I] =
    fromIterable(i)

  def fromIterable[I](is: Iterable[I]): ZStream2[Any, Nothing, I] =
    ZStream2(Process.stateful(is)(_.modify(s => if (s.isEmpty) (Pull.end, s) else (Pull.emit(s.head), s.tail)).flatten))

  def fromPull[E, I](z: Pull[E, I]): ZStream2[Any, E, I] =
    ZStream2(Process.stateful(false)(_.getAndSet(true).flatMap(if (_) Pull.end else z)))

  def repeatPull[E, I](z: Pull[E, I]): ZStream2[Any, E, I] =
    ZStream2(ZManaged.succeedNow(z))

  def repeatPullWith[R, E, I](z: Pull[E, I], s: Schedule[R, I, _]): ZStream2[R, E, I] =
    ZStream2(
      ZManaged
        .access[R](s.provide)
        .flatMap(ss =>
          ss.initial.toManaged_.flatMap(
            Process.stateful(_)(ref => z.tap(i => ref.get.flatMap(ss.update(i, _)).foldM(_ => Pull.end, ref.set)))
          )
        )
    )

  def unfold[S, I](init: S)(pull: S => (I, S)): ZStream2[Any, Nothing, I] =
    ZStream2(Process.stateful(init)(_.modify(pull)))

  def unfoldM[S, E, I](init: S)(pull: S => Pull[E, (I, S)]): ZStream2[Any, E, I] =
    ZStream2(
      Process.stateful(init)(ref =>
        ref.get.flatMap(pull).flatMap {
          case (i, s) => ref.set(s).as(i)
        }
      )
    )

  object Process {

    def stateful[S, E, I](init: S)(pull: Ref[S] => Pull[E, I]): Process[Any, E, I] =
      ZRef.makeManaged(init).map(pull)
  }
}
