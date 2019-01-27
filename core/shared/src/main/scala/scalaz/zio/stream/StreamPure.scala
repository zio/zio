package scalaz.zio.stream

import scalaz.zio._

private[stream] trait StreamPure[-R, +A] extends Stream[R, Nothing, A] { self =>
  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): UIO[S] =
    IO.succeed(foldPureLazy(s)(_ => true)(f))

  override def run[E, A0, A1 >: A, B](sink: Sink[E, A0, A1, B]): ZIO[R, E, B] =
    sink match {
      case sink: SinkPure[E, A0, A1, B] =>
        ZIO.fromEither(
          sink.extractPure(
            Sink.Step.state(
              foldPureLazy[A1, Sink.Step[sink.State, A0]](sink.initialPure)(Sink.Step.cont) { (s, a) =>
                sink.stepPure(Sink.Step.state(s), a)
              }
            )
          )
        )

      case sink: Sink[E, A0, A1, B] => super.run(sink)
    }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  override def filter(pred: A => Boolean): StreamPure[R, A] = new StreamPure[R, A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self.foldPureLazy[A, S](s)(cont) { (s, a) =>
        if (pred(a)) f(s, a)
        else s
      }

    override def fold[R1 <: R, E, A1 >: A, S]: Stream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.filter(pred).fold[R1, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def dropWhile(pred: A => Boolean): StreamPure[R, A] = new StreamPure[R, A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => cont(tp._2)) {
          case ((true, s), a) if pred(a) => true  -> s
          case ((_, s), a)               => false -> f(s, a)
        }
        ._2

    override def fold[R1 <: R, E, A1 >: A, S]: Stream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.dropWhile(pred).fold[R1, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def takeWhile(pred: A => Boolean): StreamPure[R, A] = new StreamPure[R, A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => tp._1 && cont(tp._2)) {
          case ((_, s), a) =>
            if (pred(a)) true -> f(s, a)
            else false        -> s
        }
        ._2

    override def fold[R1 <: R, E, A1 >: A, S]: Stream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.takeWhile(pred).fold[R1, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Maps over elements of the stream with the specified function.
   */
  override def map[B](f0: A => B): StreamPure[R, B] = new StreamPure[R, B] {
    override def fold[R1 <: R, E, B1 >: B, S]: Stream.Fold[R1, E, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.map(f0).fold[R1, E, B1, S].flatMap(f1 => f1(s, cont, f))
      }

    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy[A, S](s)(cont)((s, a) => f(s, f0(a)))
  }

  override def mapConcat[B](f0: A => Chunk[B]): StreamPure[R, B] = new StreamPure[R, B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy(s)(cont)((s, a) => f0(a).foldLeftLazy(s)(cont)(f))

    override def fold[R1 <: R, E, B1 >: B, S]: Stream.Fold[R1, E, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.mapConcat(f0).fold[R1, E, B1, S].flatMap(f1 => f1(s, cont, f))
      }
  }

  override def zipWithIndex[R1 <: R]: StreamPure[R1, (A, Int)] = new StreamPure[R1, (A, Int)] {
    override def foldPureLazy[A1 >: (A, Int), S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (S, Int)]((s, 0))(tp => cont(tp._1)) {
          case ((s, index), a) => (f(s, (a, index)), index + 1)
        }
        ._1

    override def fold[R2 <: R1, E, A1 >: (A, Int), S]: Stream.Fold[R2, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.zipWithIndex[R2].fold[R2, E, A1, S].flatMap(f0 => f0(s, cont, f))
      }
  }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPure[R, B] = new StreamPure[R, B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self
        .foldPureLazy[A, (S, S1)](s -> s1)(tp => cont(tp._1)) {
          case ((s, s1), a) =>
            val (s2, b) = f1(s1, a)

            f(s, b) -> s2
        }
        ._1

    override def fold[R1 <: R, E, B1 >: B, S]: Stream.Fold[R1, E, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        StreamPure.super.mapAccum(s1)(f1).fold[R1, E, B1, S].flatMap(f0 => f0(s, cont, f))
      }
  }
}

private[stream] object StreamPure extends Serializable {

  /**
   * Constructs a pure stream from the specified `Iterable`.
   */
  final def fromIterable[A](it: Iterable[A]): StreamPure[Any, A] = new StreamPure[Any, A] {
    override def fold[R1 <: Any, E >: Nothing, A1 >: A, S]: Stream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        val iterator = it.iterator

        def loop(s: S): ZIO[R1, E, S] =
          ZIO.flatten[R1, E, S] {
            ZIO.sync {
              if (iterator.hasNext && cont(s))
                f(s, iterator.next).flatMap(loop)
              else ZIO.succeed(s)
            }
          }

        loop(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = {
      val iterator = it.iterator

      def loop(s: S): S =
        if (iterator.hasNext && cont(s)) loop(f(s, iterator.next))
        else s

      loop(s)
    }
  }

  /**
   * Constructs a singleton stream from a strict value.
   */
  final def succeed[A](a: A): StreamPure[Any, A] = new StreamPure[Any, A] {
    override def fold[R <: Any, E >: Nothing, A1 >: A, S]: Stream.Fold[R, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        if (cont(s)) f(s, a)
        else IO.succeed(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }

  /**
   * Constructs a singleton stream from a lazy value.
   */
  final def succeedLazy[A](a: => A): StreamPure[Any, A] = new StreamPure[Any, A] {
    override def fold[R1 <: Any, E >: Nothing, A1 >: A, S]: Stream.Fold[R1, E, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        if (cont(s)) f(s, a)
        else IO.succeed(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }

  /**
   * Returns the empty stream.
   */
  final val empty: StreamPure[Any, Nothing] = new StreamPure[Any, Nothing] {
    override def fold[R1 <: Any, E >: Nothing, A1 >: Nothing, S]: Stream.Fold[R1, E, A1, S] =
      IO.succeedLazy((s, _, _) => IO.succeed(s))

    override def foldPureLazy[A1 >: Nothing, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = s
  }
}
