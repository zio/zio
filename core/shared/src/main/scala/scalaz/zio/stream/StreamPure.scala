package scalaz.zio.stream

import scalaz.zio._

private[stream] trait StreamPure[+A] extends Stream[Nothing, A] { self =>
  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): IO[Nothing, S] =
    IO.now(foldPureLazy(s)(_ => true)(f))

  override def run[E, A0, A1 >: A, B](sink: Sink[E, A0, A1, B]): IO[E, B] =
    sink match {
      case sink: SinkPure[E, A0, A1, B] =>
        IO.fromEither(
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
  override def filter(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self.foldPureLazy[A, S](s)(cont) { (s, a) =>
        if (pred(a)) f(s, a)
        else s
      }

    override def foldLazy[E, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      StreamPure.super.filter(pred).foldLazy(s)(cont)(f)
  }

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def dropWhile(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => cont(tp._2)) {
          case ((true, s), a) if pred(a) => true  -> s
          case ((_, s), a)               => false -> f(s, a)
        }
        ._2

    override def foldLazy[E, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      StreamPure.super.dropWhile(pred).foldLazy(s)(cont)(f)
  }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def takeWhile(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (Boolean, S)](true -> s)(tp => tp._1 && cont(tp._2)) {
          case ((_, s), a) =>
            if (pred(a)) true -> f(s, a)
            else false        -> s
        }
        ._2

    override def foldLazy[E, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      StreamPure.super.takeWhile(pred).foldLazy(s)(cont)(f)
  }

  /**
   * Maps over elements of the stream with the specified function.
   */
  override def map[B](f0: A => B): StreamPure[B] = new StreamPure[B] {
    override def foldLazy[E, B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => IO[E, S]): IO[E, S] =
      StreamPure.super.map(f0).foldLazy(s)(cont)(f)

    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy[A, S](s)(cont)((s, a) => f(s, f0(a)))
  }

  override def mapConcat[B](f0: A => Chunk[B]): StreamPure[B] = new StreamPure[B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy(s)(cont)((s, a) => f0(a).foldLeftLazy(s)(cont)(f))

    override def foldLazy[E, B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => IO[E, S]): IO[E, S] =
      StreamPure.super.mapConcat(f0).foldLazy(s)(cont)(f)
  }

  override def zipWithIndex: StreamPure[(A, Int)] = new StreamPure[(A, Int)] {
    override def foldPureLazy[A1 >: (A, Int), S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self
        .foldPureLazy[A, (S, Int)]((s, 0))(tp => cont(tp._1)) {
          case ((s, index), a) => (f(s, (a, index)), index + 1)
        }
        ._1

    override def foldLazy[E, A1 >: (A, Int), S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      StreamPure.super.zipWithIndex.foldLazy(s)(cont)(f)
  }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  override def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPure[B] = new StreamPure[B] {
    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self
        .foldPureLazy[A, (S, S1)](s -> s1)(tp => cont(tp._1)) {
          case ((s, s1), a) =>
            val (s2, b) = f1(s1, a)

            f(s, b) -> s2
        }
        ._1

    override def foldLazy[E, B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => IO[E, S]): IO[E, S] =
      StreamPure.super.mapAccum(s1)(f1).foldLazy(s)(cont)(f)
  }
}

private[stream] object StreamPure {

  /**
   * Constructs a pure stream from the specified `Iterable`.
   */
  final def fromIterable[A](it: Iterable[A]): StreamPure[A] = new StreamPure[A] {
    override def foldLazy[E >: Nothing, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] = {
      val iterator = it.iterator

      def loop(s: S): IO[E, S] =
        IO.flatten {
          IO.sync {
            if (iterator.hasNext && cont(s))
              f(s, iterator.next).flatMap(loop)
            else IO.now(s)
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
   * Constructs a singleton stream.
   */
  final def point[A](a: => A): StreamPure[A] = new StreamPure[A] {
    override def foldLazy[E >: Nothing, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      if (cont(s)) f(s, a)
      else IO.now(s)

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      if (cont(s)) f(s, a)
      else s
  }

  /**
   * Returns the empty stream.
   */
  final val empty: StreamPure[Nothing] = new StreamPure[Nothing] {
    override def foldLazy[E >: Nothing, A1 >: Nothing, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      IO.now(s)

    override def foldPureLazy[A1 >: Nothing, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S = s
  }
}
