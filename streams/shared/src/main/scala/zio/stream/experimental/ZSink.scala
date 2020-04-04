package zio.stream.experimental

import zio._

abstract class ZSink[-R, +E, -I, +Z] private (
  val push: ZManaged[R, Nothing, ZSink.Push[R, E, I, Z]]
) { self =>
  import ZSink.Push

  /**
   * Transforms this sink's input elements.
   */
  def contramap[I2](f: I2 => I): ZSink[R, E, I2, Z] =
    contramapChunks(_.map(f))

  /**
   * Effectfully transforms this sink's input elements.
   */
  def contramapM[R1 <: R, E1 >: E, I2](f: I2 => ZIO[R1, E1, I]): ZSink[R1, E1, I2, Z] =
    contramapChunksM(_.mapM(f))

  /**
   * Transforms this sink's input chunks.
   */
  def contramapChunks[I2](f: Chunk[I2] => Chunk[I]): ZSink[R, E, I2, Z] =
    ZSink(self.push.map(push => input => push(input.map(f))))

  /**
   * Effectfully transforms this sink's input chunks.
   */
  def contramapChunksM[R1 <: R, E1 >: E, I2](f: Chunk[I2] => ZIO[R1, E1, Chunk[I]]): ZSink[R1, E1, I2, Z] =
    ZSink(
      self.push.map(push =>
        input =>
          input match {
            case Some(value) => f(value).mapError(Left(_)).flatMap(is => push(Some(is)))
            case None        => push(None)
          }
      )
    )

  /**
   * Repeatedly runs the sink for as long as its results satisfy
   * the predicate `p`. The sink's results will be accumulated
   * using the stepping function `f`.
   */
  def collectAllWhileWith[S](z: S)(p: Z => Boolean)(f: (S, Z) => S): ZSink[R, E, I, S] =
    ZSink {
      Push.restartable(push).flatMap {
        case (push, restart) =>
          Ref.make(z).toManaged_.map { state => (input: Option[Chunk[I]]) =>
            push(input).catchAll {
              case Left(e) => ZIO.fail(Left(e))
              case Right(z) =>
                state
                  .updateAndGet(f(_, z))
                  .flatMap(s =>
                    if (p(z)) restart
                    else ZIO.fail(Right(s))
                  )
            }
          }
      }
    }

  /**
   * Transforms this sink's result.
   */
  def map[Z2](f: Z => Z2): ZSink[R, E, I, Z2] =
    ZSink(self.push.map(sink => (inputs: Option[Chunk[I]]) => sink(inputs).mapError(_.right.map(f))))

  /**
   * Effectfully transforms this sink's result.
   */
  def mapM[R1 <: R, E1 >: E, Z2](f: Z => ZIO[R1, E1, Z2]): ZSink[R1, E1, I, Z2] =
    ZSink(
      self.push.map(push =>
        (inputs: Option[Chunk[I]]) =>
          push(inputs).catchAll {
            case Left(e)  => Push.fail(e)
            case Right(z) => f(z).foldM(Push.fail, Push.emit)
          }
      )
    )

  /**
   * Converts this sink to a transducer that feeds incoming elements to the sink
   * and emits the sink's results as outputs. The sink will be restarted when
   * it ends.
   */
  def toTransducer: ZTransducer[R, E, I, Z] =
    ZTransducer {
      ZSink.Push.restartable(push).map {
        case (push, restart) =>
          (input: Option[Chunk[I]]) =>
            push(input).foldM(
              {
                case Left(e)  => ZIO.fail(Some(e))
                case Right(z) => restart.as(Chunk.single(z))
              },
              _ => UIO.succeed(Chunk.empty)
            )
      }
    }

  /**
   * Creates a sink that produces values until one verifies
   * the predicate `f`.
   */
  def untilOutputM[R1 <: R, E1 >: E](f: Z => ZIO[R1, E1, Boolean]): ZSink[R1, E1, I, Z] =
    ZSink {
      Push.restartable(push).map {
        case (push, restart) =>
          (is: Option[Chunk[I]]) =>
            push(is).catchAll {
              case Left(e) => ZIO.fail(Left(e))
              case Right(z) =>
                f(z).mapError(Left(_)) flatMap {
                  if (_) ZIO.fail(Right(z))
                  else restart
                }

            }
      }
    }
}

object ZSink {
  type Push[-R, +E, -I, +Z] = Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]

  object Push {
    def emit[Z](z: Z): IO[Either[Nothing, Z], Nothing]        = IO.fail(Right(z))
    def fail[E](e: E): IO[Either[E, Nothing], Nothing]        = IO.fail(Left(e))
    def halt[E](c: Cause[E]): IO[Either[E, Nothing], Nothing] = IO.halt(c).mapError(Left(_))

    /**
     * Decorates a Push with a ZIO value that re-initializes it with a fresh state.
     */
    def restartable[R, E, I, Z](
      sink: ZManaged[R, Nothing, Push[R, E, I, Z]]
    ): ZManaged[R, Nothing, (Push[R, E, I, Z], ZIO[R, Nothing, Unit])] =
      for {
        switchSink  <- ZManaged.switchable[R, Nothing, Push[R, E, I, Z]]
        initialSink <- switchSink(sink).toManaged_
        currSink    <- Ref.make(initialSink).toManaged_
        restart     = switchSink(sink).flatMap(currSink.set)
        newPush     = (input: Option[Chunk[I]]) => currSink.get.flatMap(_.apply(input))
      } yield (newPush, restart)
  }

  def apply[R, E, I, Z](push: ZManaged[R, Nothing, Push[R, E, I, Z]]) =
    new ZSink(push) {}

  /**
   * A sink that collects all of its inputs into a list.
   */
  def collectAll[A]: ZSink[Any, Nothing, A, List[A]] =
    ZSink {
      for {
        as <- ZRef.makeManaged[Chunk[A]](Chunk.empty)
        push = (xs: Option[Chunk[A]]) =>
          xs match {
            case Some(xs) => as.update(_ ++ xs)
            case None     => as.get.flatMap(as => Push.emit(as.toList))
          }
      } yield push
    }

  /**
   * A sink that counts the number of elements fed to it.
   */
  val count: ZSink[Any, Nothing, Any, Long] =
    fold(0L)((s, _) => s + 1)

  /**
   * Creates a sink halting with a specified cause.
   */
  def halt[E](e: => Cause[E]): ZSink[Any, E, Any, Nothing] =
    ZSink.fromPush(_ => Push.halt(e))

  /**
   * Creates a sink halting with the specified `Throwable`.
   */
  def die(e: => Throwable): ZSink[Any, Nothing, Any, Nothing] =
    ZSink.halt(Cause.die(e))

  /**
   * Creates a sink halting with the specified message, wrapped in a
   * `RuntimeException`.
   */
  def dieMessage(m: => String): ZSink[Any, Nothing, Any, Nothing] =
    ZSink.halt(Cause.die(new RuntimeException(m)))

  def fromPush[R, E, I, Z](sink: Push[R, E, I, Z]): ZSink[R, E, I, Z] =
    ZSink(Managed.succeed(sink))

  /**
   * A sink that immediately ends with the specified value.
   */
  def succeed[Z](z: Z): ZSink[Any, Nothing, Any, Z] =
    fromPush {
      case Some(_) => UIO.unit
      case None    => ZIO.fail(Right(z))
    }

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    ZSink {
      for {
        state <- Ref.make(z).toManaged_
        push = (is: Option[Chunk[I]]) =>
          is match {
            case None => state.get.flatMap(s => ZIO.fail(Right(s)))
            case Some(is) =>
              state.get
                .flatMap(is.foldM(_)(f))
                .flatMap(state.set)
                .mapError(Left(_))
          }
      } yield push
    }

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def fold[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    foldM(z)((s, i) => UIO.succeedNow(f(s, i)))

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, Z](b: => ZIO[R, E, Z]): ZSink[R, E, Any, Z] =
    fromPush {
      case None => b.foldM(Push.fail, Push.emit)
      case _    => b.foldM(Push.fail, _ => UIO.unit)
    }
}
