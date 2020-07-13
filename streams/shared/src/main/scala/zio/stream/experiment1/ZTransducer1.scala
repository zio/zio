package zio.stream.experiment1

import zio._

abstract class ZTransducer1[-R, -EI, +EO, -I, +O] {

  def process: ZTransducer1.Process[R, EI, EO, I, O]

  def >>>[R1 <: R, E1 >: EO, E2, O1](downstream: ZTransducer1[R1, E1, E2, O, O1]): ZTransducer1[R1, EI, E2, I, O1] =
    chain(downstream)

  def chain[R1 <: R, E1 >: EO, E2, O1](downstream: ZTransducer1[R1, E1, E2, O, O1]): ZTransducer1[R1, EI, E2, I, O1] =
    ZTransducer1(process.zipWith(downstream.process)(_ andThen _))
}

object ZTransducer1 {

  type Process[-R, -EI, +EO, -I, +O] = URManaged[R, Pull[EI, I] => Pull[EO, O]]

  def apply[R, EI, EO, I, O](p: Process[R, EI, EO, I, O]): ZTransducer1[R, EI, EO, I, O] =
    new ZTransducer1[R, EI, EO, I, O] {
      def process: Process[R, EI, EO, I, O] = p
    }

  def die[E, I, O](t: => Throwable): Chunked[Any, E, I, O] =
    new Chunked[Any, E, I, O](ZManaged.die(t)) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[O]] =
        ZTransducer1.die[E, Chunk[I], Chunk[O]](t)
    }

  def dieMessage[E, I, O](message: => String): Chunked[Any, E, I, O] =
    new Chunked[Any, E, I, O](ZManaged.dieMessage(message)) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[O]] =
        ZTransducer1.dieMessage[E, Chunk[I], Chunk[O]](message)
    }

  def filter[E, I](p: I => Boolean): Chunked[Any, E, I, I] =
    new Chunked[Any, E, I, I](Process.filter(p)) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[I]] =
        ZTransducer1.map(_.filter(p))
    }

  def fold[S, E, I, O](init: S)(push: (S, I) => (O, S)): Chunked[Any, E, I, O] =
    new Chunked[Any, E, I, O](Process.fold(init)(push)) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[O]] =
        ZTransducer1.fold(init) { (s, is: Chunk[I]) =>
          val b = ChunkBuilder.make[O]()
          var z = s
          is.foreach { i =>
            val os = push(z, i)
            b += os._1
            z = os._2
          }
          (b.result(), z)
        }
    }

  def identity[E, I]: Chunked[Any, E, I, I] =
    new Chunked[Any, E, I, I](ZManaged.succeedNow(pull => pull)) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[I]] =
        ZTransducer1.identity[E, Chunk[I]]
    }

  def make[R, E, I, O](process: Process[R, E, E, I, O]): Chunked[R, E, I, O] =
    new Chunked(process)

  def map[E, I, O](f: I => O): Chunked[Any, E, I, O] =
    new Chunked[Any, E, I, O](Process.map(f)) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[O]] =
        ZTransducer1.map[E, Chunk[I], Chunk[O]](_.map(f))
    }

  def take[E, I](n: Long): Chunked[Any, E, I, I] =
    if (n < 0) dieMessage(s"cannot take $n")
    else
      new Chunked[Any, E, I, I](Process.foldWhile(n)(_ > 0)((s, i) => (i, s - 1))) {

        override def chunked: Chunked[Any, E, Chunk[I], Chunk[I]] =
          ZTransducer1.make[Any, E, Chunk[I], Chunk[I]](
            Process.foldWhile(n)(_ > 0)((s, c) => if (c.length <= s) (c, s - c.length) else (c.take(s.toInt), 0))
          )
      }

  def takeUntil[E, I](p: I => Boolean): Chunked[Any, E, I, I] =
    new Chunked[Any, E, I, I](Process.doUntil(i => i -> p(i))) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[I]] =
        ZTransducer1.make[Any, E, Chunk[I], Chunk[I]](
          Process.doUntil(c =>
            c.indexWhere(p) match {
              case -1 => (c, false)
              case i  => (c.take(i), true)
            }
          )
        )
    }

  def takeWhile[E, I](p: I => Boolean): Chunked[Any, E, I, I] =
    new Chunked[Any, E, I, I](Process.doWhile(i => i -> p(i))) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[I]] =
        ZTransducer1.make[Any, E, Chunk[I], Chunk[I]](
          Process.doWhile(c =>
            c.indexWhere(!p(_)) match {
              case -1 => c             -> true
              case i  => c.take(i - 1) -> false
            }
          )
        )
    }

  def takeWhileM[E, I](p: I => Boolean, leftover: I => IO[E, Any]): Chunked[Any, E, I, I] =
    new Chunked[Any, E, I, I](
      Process.doWhileM(i => if (p(i)) Pull.emit(i -> true) else Pull.fromEffect(leftover(i)) *> Pull.end)
    ) {

      override def chunked: Chunked[Any, E, Chunk[I], Chunk[I]] =
        ZTransducer1.make[Any, E, Chunk[I], Chunk[I]](
          Process.doWhileM(c =>
            c.indexWhere(!p(_)) match {
              case -1 => Pull.emit(c                                                                          -> true)
              case i  => Pull.fromEffect(ZIO.foreach(i until c.length)(i => leftover(c(i)))).as(c.take(i - 1) -> false)
            }
          )
        )
    }

  sealed class Chunked[R, E, I, O](val process: Process[R, E, E, I, O]) extends ZTransducer1[R, E, E, I, O] {

    def chunked: Chunked[R, E, Chunk[I], Chunk[O]] =
      ZTransducer1.make(process.map(pipe => _.flatMap(ZIO.foreach(_)(i => pipe(Pull.emit(i))))))
  }

  object Process {

    def doUntil[E, I, O](push: I => (O, Boolean)): Process[Any, E, E, I, O] =
      foldWhile(false)(_ == false)((_, i) => push(i))

    def doWhile[E, I, O](push: I => (O, Boolean)): Process[Any, E, E, I, O] =
      foldWhile(true)(_ == true)((_, i) => push(i))

    def doWhileM[E, I, O](push: I => Pull[E, (O, Boolean)]): Process[Any, E, E, I, O] =
      foldWhileM(true)(_ == true)((_, i) => push(i))

    def filter[E, I](p: I => Boolean): Process[Any, E, E, I, I] =
      ZManaged.succeedNow { pull =>
        def go: Pull[E, I] =
          pull.flatMap(i => if (p(i)) ZIO.succeedNow(i) else go)
        go
      }

    def fold[S, E, I, O](init: S)(push: (S, I) => (O, S)): Process[Any, E, E, I, O] =
      ZRef.makeManaged(init).map(ref => _.flatMap(i => ref.modify(push(_, i))))

    def foldWhile[S, E, I, O](init: S)(p: S => Boolean)(push: (S, I) => (O, S)): Process[Any, E, E, I, O] =
      ZRef
        .makeManaged(init)
        .map(ref =>
          pull =>
            ref.get.flatMap(s =>
              if (p(s)) pull.flatMap { i =>
                val (o, z) = push(s, i)
                ref.set(z).as(o)
              }
              else Pull.end
            )
        )

    def foldWhileM[S, E, I, O](init: S)(p: S => Boolean)(push: (S, I) => Pull[E, (O, S)]): Process[Any, E, E, I, O] =
      ZRef
        .makeManaged(init)
        .map(ref =>
          pull =>
            ref.get.flatMap(s =>
              if (p(s)) pull.flatMap(push(s, _)).flatMap(os => ref.set(os._2).as(os._1))
              else Pull.end
            )
        )

    def map[E, I, O](f: I => O): Process[Any, E, E, I, O] =
      ZManaged.succeedNow(_.map(f))
  }
}
