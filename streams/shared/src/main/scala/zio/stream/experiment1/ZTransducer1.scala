package zio.stream.experiment1

import zio._

abstract class ZTransducer1[-R, +E, -I, +O] {
  self =>

  def process[EE >: E]: ZTransducer1.Process[R, EE, I, O]

  def >>:[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZStream1[R1, E1, O] =
    prepend(upstream)

  def >>:[R1 <: R, E1 >: E, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZTransducer1[R1, E1, II, O] =
    transduce(upstream)

  def chunked: ZTransducer1[R, E, Chunk[I], Chunk[O]] =
    new ZTransducer1[R, E, Chunk[I], Chunk[O]] {

      def process[EE >: E]: ZTransducer1.Process[R, EE, Chunk[I], Chunk[O]] =
        self.process[EE].map(f => _.flatMap(ZIO.foreach(_)(i => f(Pull.emit(i)))))
    }

  def prepend[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZStream1[R1, E1, O] =
    ZStream1(self.process[E1].zipWith(upstream.process)(_ apply _))

  def transduce[R1 <: R, E1 >: E, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZTransducer1[R1, E1, II, O] =
    new ZTransducer1[R1, E1, II, O] {

      def process[EE >: E1]: ZTransducer1.Process[R1, EE, II, O] =
        upstream.process[EE].zipWith(self.process[EE])(_ andThen _)
    }
}

object ZTransducer1 {

  type Process[-R, E, -I, +O] = URManaged[R, Pull[E, I] => Pull[E, O]]

  def access[R, I]: AccessPartiallyApplied[R, I] =
    new AccessPartiallyApplied[R, I]()

  def collect[I, O](f: PartialFunction[I, O]): ZTransducer1[Any, Nothing, I, O] =
    new ZTransducer1[Any, Nothing, I, O] {

      def process[EE >: Nothing]: Process[Any, EE, I, O] =
        ZManaged.succeedNow { pull =>
          def go: Pull[EE, O] =
            pull.flatMap(i => if (f.isDefinedAt(i)) Pull.emit(f(i)) else go)
          go
        }
    }

  def die(t: => Throwable): ZTransducer1[Any, Nothing, Any, Nothing] =
    new ZTransducer1[Any, Nothing, Any, Nothing] {

      def process[EE >: Nothing]: Process[Any, EE, Any, Nothing] =
        ZManaged.die(t)

      override def chunked: ZTransducer1[Any, Nothing, Chunk[Any], Chunk[Nothing]] =
        ZTransducer1.die(t)
    }

  def dieMessage(message: => String): ZTransducer1[Any, Nothing, Any, Nothing] =
    die(new RuntimeException(message))

  def filter[I](p: I => Boolean): ZTransducer1[Any, Nothing, I, I] =
    new ZTransducer1[Any, Nothing, I, I] {

      def process[EE >: Nothing]: Process[Any, EE, I, I] =
        ZManaged.succeedNow(_.doUntil(p))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer1.map(_.filter(p))
    }

  val end: ZTransducer1[Any, Nothing, Any, Nothing] =
    new ZTransducer1[Any, Nothing, Any, Nothing] {

      def process[EE >: Nothing]: Process[Any, EE, Any, Nothing] =
        ZManaged.succeedNow(_ => Pull.end)

      override def chunked: ZTransducer1[Any, Nothing, Chunk[Any], Chunk[Nothing]] =
        ZTransducer1.end
    }

  def fold[S, I, O](init: S)(pipe: (S, I) => (O, S)): ZTransducer1[Any, Nothing, I, O] =
    new ZTransducer1[Any, Nothing, I, O] {

      def process[EE >: Nothing]: Process[Any, EE, I, O] =
        Process.stateful(init)((ref, pull) => pull.flatMap(i => ref.modify(pipe(_, i))))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[O]] =
        ZTransducer1.fold(init) { (s, is: Chunk[I]) =>
          val b = ChunkBuilder.make[O]()
          var z = s
          is.foreach { i =>
            val os = pipe(z, i)
            b += os._1
            z = os._2
          }
          (b.result(), z)
        }
    }

  def foldM[S, E, I, O](init: S)(pipe: (S, I) => Pull[E, (O, S)]): ZTransducer1[Any, E, I, O] =
    new ZTransducer1[Any, E, I, O] {

      def process[EE >: E]: Process[Any, EE, I, O] =
        Process.stateful(init)((ref, pull) =>
          for {
            s  <- ref.get
            i  <- pull
            os <- pipe(s, i)
            _  <- ref.set(os._2)
          } yield os._1
        )
    }

  def identity[I]: ZTransducer1[Any, Nothing, I, I] =
    new ZTransducer1[Any, Nothing, I, I] {

      def process[EE >: Nothing]: Process[Any, EE, I, I] =
        ZManaged.succeedNow(pull => pull)

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer1.identity[Chunk[I]]
    }

  def map[I, O](f: I => O): ZTransducer1[Any, Nothing, I, O] =
    new ZTransducer1[Any, Nothing, I, O] {

      def process[EE >: Nothing]: Process[Any, EE, I, O] =
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

        def process[EE >: Nothing]: Process[Any, EE, I, I] =
          Process.stateful(n)((ref, pull) => ref.modify(n => if (n > 0) (pull, n - 1) else (Pull.end, n)).flatten)

        override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
          new ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] {

            def process[EE >: Nothing]: Process[Any, EE, Chunk[I], Chunk[I]] =
              Process.stateful(n)((ref, pull) =>
                ref.get.flatMap(n =>
                  if (n > 0)
                    pull.flatMap(i =>
                      if (i.length >= n) ref.set(0).as(i.take(n.toInt)) else ref.set(n - i.length).as(i)
                    )
                  else Pull.end
                )
              )
          }
      }

  def takeUntil[I](p: I => Boolean): ZTransducer1[Any, Nothing, I, I] =
    new ZTransducer1[Any, Nothing, I, I] {

      def process[EE >: Nothing]: Process[Any, EE, I, I] =
        Process.stateful(false)((ref, pull) => ref.get.flatMap(if (_) Pull.end else pull.tap(i => ref.set(p(i)))))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
        new ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] {

          def process[EE >: Nothing]: Process[Any, EE, Chunk[I], Chunk[I]] =
            Process.stateful(false)((ref, pull) =>
              ref.get.flatMap(
                if (_) Pull.end
                else
                  pull.flatMap(is =>
                    is.indexWhere(p) match {
                      case -1 => Pull.emit(is)
                      case i  => ref.set(true).as(is.take(i + 1))
                    }
                  )
              )
            )
        }
    }

  def takeWhile[I](p: I => Boolean): ZTransducer1[Any, Nothing, I, I] =
    new ZTransducer1[Any, Nothing, I, I] {

      def process[EE >: Nothing]: Process[Any, EE, I, I] =
        Process.stateless(_.flatMap(i => if (p(i)) Pull.emit(i) else Pull.end))

      override def chunked: ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] =
        new ZTransducer1[Any, Nothing, Chunk[I], Chunk[I]] {

          def process[EE >: Nothing]: Process[Any, EE, Chunk[I], Chunk[I]] =
            Process.stateful(true)((ref, pull) =>
              ref.get.flatMap(
                if (_)
                  pull.flatMap(is =>
                    is.indexWhere(!p(_)) match {
                      case -1 => Pull.emit(is)
                      case i  => ref.set(false).as(is.take(i))
                    }
                  )
                else Pull.end
              )
            )
        }
    }

  final class AccessPartiallyApplied[R, I](private val dummy: Boolean = true) extends AnyVal {

    def apply[E, O](pipe: (R, I) => Pull[E, O]): ZTransducer1[R, E, I, O] =
      new ZTransducer1[R, E, I, O] {
        def process[EE >: E]: Process[R, EE, I, O] =
          ZManaged.access[R](r => _.flatMap(pipe(r, _)))
      }
  }

  final class ServicePartiallyApplied[A, I](private val dummy: Boolean = true) extends AnyVal {

    def apply[E, O](pipe: (A, I) => Pull[E, O])(implicit a: Tag[A]): ZTransducer1[Has[A], E, I, O] =
      new ZTransducer1[Has[A], E, I, O] {
        def process[EE >: E]: Process[Has[A], EE, I, O] =
          ZManaged.service[A].map(a => _.flatMap(pipe(a, _)))
      }
  }

  object Process {

    def stateless[E, I, O](pipe: Pull[E, I] => Pull[E, O]): Process[Any, E, I, O] =
      ZManaged.succeedNow(pipe)

    def stateful[S, E, I, O](init: S)(pipe: (Ref[S], Pull[E, I]) => Pull[E, O]): Process[Any, E, I, O] =
      ZRef.makeManaged(init).map(ref => pull => pipe(ref, pull))
  }
}
