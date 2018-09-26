package scalaz.zio.stream

import scalaz.zio._

private[stream] trait StreamPure[+A] extends Stream[Nothing, A] { self =>
  import Stream.Step

  /**
   * Executes a fold over the stream of values.
   */
  def foldPure[A1 >: A, S](s: S)(f: (S, A1) => Step[S]): Step[S] =
    foldPureLazy[A1, Step[S]](Step.cont(s)) {
      case Step.Cont(_) => true
      case _            => false
    }((s, a) => f(s.extract, a))

  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
    foldPure[A1, S](s) { (s, a) =>
      if (cont(s)) Step.cont(f(s, a))
      else Step.stop(s)
    }.extract

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
    override def foldPure[A1 >: A, S](s: S)(f: (S, A1) => Step[S]): Step[S] =
      self.foldPure[A, S](s) { (s, a) =>
        if (pred(a)) f(s, a)
        else Step.cont(s)
      }

    override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
      self.foldPureLazy[A, S](s)(cont) { (s, a) =>
        if (pred(a)) f(s, a)
        else s
      }

    override def fold[E, A1 >: A, S](s: S)(f: (S, A1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.filter(pred).fold(s)(f)

    override def foldLazy[E, A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => IO[E, S]): IO[E, S] =
      StreamPure.super.filter(pred).foldLazy(s)(cont)(f)
  }

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def dropWhile(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPure[A1 >: A, S](s: S)(f: (S, A1) => Step[S]): Step[S] =
      (self
        .foldPure[A, (Boolean, S)](true -> s) {
          case ((true, s), a) if pred(a) => Step.Cont(true    -> s)
          case ((_, s), a)               => f(s, a).map(false -> _)
        })
        .map(_._2)

    override def fold[E, A1 >: A, S](s: S)(f: (S, A1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.dropWhile(pred).fold(s)(f)
  }

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  override def takeWhile(pred: A => Boolean): StreamPure[A] = new StreamPure[A] {
    override def foldPure[A1 >: A, S](s: S)(f: (S, A1) => Step[S]): Step[S] =
      self.foldPure[A, S](s)((s, a) => if (pred(a)) f(s, a) else Step.Stop(s))

    override def fold[E, A1 >: A, S](s: S)(f: (S, A1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.takeWhile(pred).fold(s)(f)
  }

  /**
   * Maps over elements of the stream with the specified function.
   */
  override def map[B](f0: A => B): StreamPure[B] = new StreamPure[B] {
    override def fold[E, B1 >: B, S](s: S)(f: (S, B1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.map(f0).fold(s)(f)

    override def foldLazy[E, B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => IO[E, S]): IO[E, S] =
      StreamPure.super.map(f0).foldLazy(s)(cont)(f)

    override def foldPure[B1 >: B, S](s: S)(f: (S, B1) => Step[S]): Step[S] =
      self.foldPure[A, S](s)((s, a) => f(s, f0(a)))

    override def foldPureLazy[B1 >: B, S](s: S)(cont: S => Boolean)(f: (S, B1) => S): S =
      self.foldPureLazy[A, S](s)(cont)((s, a) => f(s, f0(a)))
  }

  override def mapConcat[B](f0: A => Chunk[B]): StreamPure[B] = new StreamPure[B] {
    override def fold[E, B1 >: B, S](s: S)(f: (S, B1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.mapConcat(f0).fold(s)(f)

    override def foldPure[B1 >: B, S](s: S)(f: (S, B1) => Step[S]): Step[S] = {
      def loop(s: S, c: Chunk[B], i: Int): Step[S] =
        if (i >= c.length) Step.cont(s)
        else
          f(s, c(i)) match {
            case Step.Cont(s) => loop(s, c, i + 1)
            case s            => s
          }

      self.foldPure[A, S](s)((s, a) => loop(s, f0(a), 0))
    }
  }

  override def zipWithIndex: StreamPure[(A, Int)] = new StreamPure[(A, Int)] {
    override def fold[E, A1 >: (A, Int), S](s: S)(f: (S, A1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.zipWithIndex.fold(s)(f)

    override def foldPure[A1 >: (A, Int), S](s: S)(f: (S, A1) => Step[S]): Step[S] =
      self
        .foldPure[A, (S, Int)]((s, 0)) {
          case ((s, index), a) => f(s, (a, index)).map(s => (s, index + 1))
        }
        .map(_._1)
  }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  override def scan[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): StreamPure[B] = new StreamPure[B] {
    override def fold[E, B1 >: B, S](s: S)(f: (S, B1) => IO[E, Step[S]]): IO[E, Step[S]] =
      StreamPure.super.scan(s1)(f1).fold(s)(f)

    override def foldPure[B1 >: B, S](s: S)(f: (S, B1) => Step[S]): Step[S] =
      self
        .foldPure[A, (S, S1)](s -> s1) {
          case ((s, s1), a) =>
            val (s2, b) = f1(s1, a)

            f(s, b).map(_ -> s2)
        }
        .map(_._1)
  }
}
