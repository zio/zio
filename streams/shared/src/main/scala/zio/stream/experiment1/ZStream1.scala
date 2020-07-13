package zio.stream.experiment1

import zio._

sealed abstract class ZStream1[-R, +E, +I](val process: ZStream1.Process[R, E, I]) {

  def :>>[R1 <: R, E1 >: E, I1](downstream: ZTransducer1[R1, E1, E1, I, I1]): ZStream1[R1, E1, I1] =
    transduce(downstream)

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

  def transduce[R1 <: R, E1 >: E, I1](downstream: ZTransducer1[R1, E1, E1, I, I1]): ZStream1[R1, E1, I1] =
    ZStream1(process.zipWith(downstream.process)((pull, pipe) => pipe(pull)))

  def run[R1 <: R, E1 >: E, O](downstream: ZSink1[R1, E1, E1, I, O]): ZIO[R1, E1, O] =
    process
      .zipWith(downstream.process) {
        case (pull, (push, read)) =>
          push(pull).forever.foldCauseM(Cause.sequenceCauseOption(_).fold(read)(ZIO.halt(_)), ZIO.succeedNow)
      }
      .use(identity)

  def runDrain: ZIO[R, E, Unit] =
    run(ZSink1.drain)

  def take(n: Long): ZStream1[R, E, I] =
    transduce(ZTransducer1.take(n))

  def takeUntil(p: I => Boolean): ZStream1[R, E, I] =
    transduce(ZTransducer1.takeUntil(p))

  def takeWhile(p: I => Boolean): ZStream1[R, E, I] =
    transduce(ZTransducer1.takeWhile(p))
}

object ZStream1 {

  type Process[-R, +E, +I] = URManaged[R, Pull[E, I]]

  def apply[R, E, I](process: Process[R, E, I]): ZStream1[R, E, I] =
    new ZStream1(process) {}

  def apply[I](i: I*): ZStream1[Any, Nothing, I] =
    fromIterable(i)

  def fromIterable[I](is: Iterable[I]): ZStream1[Any, Nothing, I] =
    ZStream1(Process.pull(is)(s => if (s.isEmpty) (Pull.end, s) else (Pull.emit(s.head), s.tail)))

  def fromChunk[I](chunk: Chunk[I]): ZStream1[Any, Nothing, I] =
    ZStream1(Process.pull(chunk)(s => if (s.isEmpty) (Pull.end, s) else (Pull.emit(s.head), s.tail)))

  def managed[R, E, I](z: ZManaged[R, E, I]): ZStream1[R, E, I] =
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

  def repeatEffect[E, A](z: IO[E, A]): ZStream1[Any, E, A] =
    repeatPull(Pull.fromEffect(z))

  def repeatPull[E, A](z: Pull[E, A]): ZStream1[Any, E, A] =
    ZStream1(ZManaged.succeedNow(z))

  object Process {

    def pull[R, E, I, S](init: S)(pull: S => (Pull[E, I], S)): Process[R, E, I] =
      ZRef.makeManaged(init).map(ref => ref.modify(pull).flatten)
  }
}
