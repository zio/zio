package zio.stream.experiment1

import zio._

abstract class ZTransducer1[-R, +EO, -I, +O] {
  self =>

  def process[E >: EO]: ZTransducer1.Process[R, E, I, O]

  def >>:[R1 <: R, E1 >: EO, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZStream1[R1, E1, O] =
    prepend(upstream)

  def >>:[R1 <: R, E1 >: EO, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZTransducer1[R1, E1, II, O] =
    transduce(upstream)

  def chunked: ZTransducer1[R, EO, Chunk[I], Chunk[O]] =
    new ZTransducer1[R, EO, Chunk[I], Chunk[O]] {

      def process[E >: EO]: ZTransducer1.Process[R, E, Chunk[I], Chunk[O]] =
        self.process[E].map(f => _.flatMap(ZIO.foreach(_)(i => f(Pull.emit(i)))))
    }

  def prepend[R1 <: R, E1 >: EO, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZStream1[R1, E1, O] =
    ZStream1(self.process[E1].zipWith(upstream.process)(_ apply _))

  def transduce[R1 <: R, E1 >: EO, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZTransducer1[R1, E1, II, O] =
    new ZTransducer1[R1, E1, II, O] {

      def process[E >: E1]: ZTransducer1.Process[R1, E, II, O] =
        upstream.process[E].zipWith(self.process[E])(_ andThen _)
    }
}

object ZTransducer1 {

  type Process[-R, E, -I, +O] = URManaged[R, Pull[E, I] => Pull[E, O]]

  def access[R, I]: AccessPartiallyApplied[R, I] =
    new AccessPartiallyApplied[R, I]()

  def die(t: => Throwable): ZTransducer1[Any, Nothing, Any, Nothing] =
    new ZTransducer1 {

      def process[E >: Nothing]: Process[Any, E, Any, Nothing] =
        ZManaged.die(t)

      override def chunked: ZTransducer1[Any, Nothing, Chunk[Any], Chunk[Nothing]] =
        ZTransducer1.die(t)
    }

  def dieMessage(message: => String): ZTransducer1[Any, Nothing, Any, Nothing] =
    die(new RuntimeException(message))

  def filter[I](p: I => Boolean): ZTransducer1[Any, Nothing, I, I] =
    new ZTransducer1[Any, Nothing, I, I] {

      def process[E >: Nothing]: Process[Any, E, I, I] =
        ZManaged.succeedNow(_.doUntil(p))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer1.map(_.filter(p))
    }

  val end: ZTransducer1[Any, Nothing, Any, Nothing] =
    new ZTransducer1[Any, Nothing, Any, Nothing] {

      def process[E >: Nothing]: Process[Any, E, Any, Nothing] =
        ZManaged.succeedNow(_ => Pull.end)

      override def chunked: ZTransducer1[Any, Nothing, Chunk[Any], Chunk[Nothing]] =
        ZTransducer1.end
    }

  def fold[S, I, O](init: S)(push: (S, I) => (O, S)): ZTransducer1[Any, Nothing, I, O] =
    new ZTransducer1[Any, Nothing, I, O] {

      def process[E >: Nothing]: Process[Any, E, I, O] =
        Process.stateful(init)((ref, pull) => pull.flatMap(i => ref.modify(push(_, i))))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[O]] =
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

  def foldM[S, EO, I, O](init: S)(push: (S, I) => Pull[EO, (O, S)]): ZTransducer1[Any, EO, I, O] =
    new ZTransducer1[Any, EO, I, O] {

      def process[E >: EO]: Process[Any, E, I, O] =
        Process.stateful(init)((ref, pull) =>
          for {
            s  <- ref.get
            i  <- pull
            os <- push(s, i)
            _  <- ref.set(os._2)
          } yield os._1
        )
    }

  def foldUntil[S, I, O](init: S)(p: S => Boolean)(push: (S, I) => (O, S)): ZTransducer1[Any, Nothing, I, O] =
    if (p(init)) end
    else
      new ZTransducer1[Any, Nothing, I, O] {
        self =>

        def process[E >: Nothing]: Process[Any, E, I, O] =
          Process.stateful(init)((ref, pull) =>
            ref.get.flatMap(s =>
              if (p(s)) Pull.end
              else
                pull.flatMap { i =>
                  val (o, z) = push(s, i)
                  ref.set(z).as(o)
                }
            )
          )

        override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[O]] =
          ZTransducer1.foldUntil(init)(p) { (s, is: Chunk[I]) =>
            val b = ChunkBuilder.make[O]()
            var z = s
            var i = 0
            while (i < is.length) {
              val os = push(z, is(i))
              z = os._2
              b += os._1
              if (p(z)) i = is.length else i += 1
            }
            b.result() -> z
          }
      }

  def foldWhile[S, I, O](init: S)(p: S => Boolean)(push: (S, I) => (O, S)): ZTransducer1[Any, Nothing, I, O] =
    if (p(init))
      new ZTransducer1[Any, Nothing, I, O] {
        self =>

        def process[E >: Nothing]: Process[Any, E, I, O] =
          Process.stateful(init)((ref, pull) =>
            ref.get.flatMap(s =>
              if (p(s)) pull.flatMap { i =>
                val (o, z) = push(s, i)
                ref.set(z).as(o)
              }
              else Pull.end
            )
          )

        override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[O]] =
          ZTransducer1.foldWhile(init)(p) { (s, is: Chunk[I]) =>
            val b = ChunkBuilder.make[O]()
            var z = s
            var i = 0
            while (i < is.length) {
              val os = push(z, is(i))
              z = os._2
              b += os._1
              if (p(z)) i += 1 else i = is.length
            }
            b.result() -> z
          }
      }
    else end

  def identity[I]: ZTransducer1[Any, Nothing, I, I] =
    new ZTransducer1[Any, Nothing, I, I] {

      def process[E >: Nothing]: Process[Any, E, I, I] =
        ZManaged.succeedNow(pull => pull)

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer1.identity[Chunk[I]]
    }

  def map[I, O](f: I => O): ZTransducer1[Any, Nothing, I, O] =
    new ZTransducer1[Any, Nothing, I, O] {

      def process[E >: Nothing]: Process[Any, E, I, O] =
        ZManaged.succeedNow(_.map(f))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[O]] =
        ZTransducer1.map[Chunk[I], Chunk[O]](_.map(f))
    }

  def service[A, I]: ServicePartiallyApplied[A, I] =
    new ServicePartiallyApplied[A, I]()

  def take[I](n: Long): ZTransducer1[Any, Nothing, I, I] =
    if (n < 0) dieMessage(s"cannot take $n")
    else
      new ZTransducer1[Any, Nothing, I, I] {

        def process[E >: Nothing]: Process[Any, E, I, I] =
          Process.stateful(n)((ref, pull) => ref.modify(n => if (n > 0) (pull, n - 1) else (Pull.end, n)).flatten)

        override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
          ZTransducer1.foldWhile(n)(_ > 0)((n, i) => if (i.length >= n) (i.take(n.toInt), 0) else (i, n - i.length))
      }

  def takeUntil[I](p: I => Boolean): ZTransducer1[Any, Nothing, I, I] =
    ZTransducer1.foldUntil(false)(_ == true)((_, i) => (i, p(i)))

  def takeWhile[I](p: I => Boolean): ZTransducer1[Any, Nothing, I, I] =
    ZTransducer1.foldWhile(true)(_ == true)((_, i) => (i, p(i)))

  final class AccessPartiallyApplied[R, I](private val dummy: Boolean = true) extends AnyVal {

    def apply[EO, O](push: (R, I) => Pull[EO, O]): ZTransducer1[R, EO, I, O] =
      new ZTransducer1[R, EO, I, O] {
        def process[E >: EO]: Process[R, E, I, O] =
          ZManaged.access[R](r => _.flatMap(push(r, _)))
      }
  }

  final class ServicePartiallyApplied[A, I](private val dummy: Boolean = true) extends AnyVal {

    def apply[EO, O](push: (A, I) => Pull[EO, O])(implicit a: Tag[A]): ZTransducer1[Has[A], EO, I, O] =
      new ZTransducer1[Has[A], EO, I, O] {
        def process[E >: EO]: Process[Has[A], E, I, O] =
          ZManaged.service[A].map(a => _.flatMap(push(a, _)))
      }
  }

  object Process {

    def stateful[S, E, I, O](init: S)(push: (Ref[S], Pull[E, I]) => Pull[E, O]): Process[Any, E, I, O] =
      ZRef.makeManaged(init).map(ref => pull => push(ref, pull))
  }
}
