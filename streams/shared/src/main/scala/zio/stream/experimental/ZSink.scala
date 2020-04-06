package zio.stream.experimental

import zio._

// Important notes while writing sinks and combinators:
// - What return values for sinks mean:
//   ZIO.unit - "need more values"
//   ZIO.fail(Right(z)) - "ended with z"
//   ZIO.fail(Left(e)) - "failed with e"
// - Always consume entire chunks. If a sink consumes part of a chunk and drops the rest,
//   it should probably be a transducer.
// - Sinks should always end when receiving a `None`. It is a defect to not end with some
//   sort of result (even a failure) when receiving a `None`.
// - Sinks can assume they will not be pushed again after emitting a value.
abstract class ZSink[-R, +E, -I, +Z] private (
  val push: ZManaged[R, Nothing, ZSink.Push[R, E, I, Z]]
) { self =>
  import ZSink.Push

  /**
   * Replaces this sink's result with the provided value.
   */
  def as[Z2](z: => Z2): ZSink[R, E, I, Z2] =
    map(_ => z)

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
            input match {
              case None => state.get.map(Right(_)).flip
              case is @ Some(_) =>
                push(is).catchAll {
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
    }

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
   * Transforms both inputs and result of this sink using the provided functions.
   */
  def dimap[I2, Z2](f: I2 => I, g: Z => Z2): ZSink[R, E, I2, Z2] =
    contramap(f).map(g)

  /**
   * Effectfully transforms both inputs and result of this sink using the provided functions.
   */
  def dimapM[R1 <: R, E1 >: E, I2, Z2](
    f: I2 => ZIO[R1, E1, I],
    g: Z => ZIO[R1, E1, Z2]
  ): ZSink[R1, E1, I2, Z2] =
    contramapM(f).mapM(g)

  /**
   * Transforms both input chunks and result of this sink using the provided functions.
   */
  def dimapChunks[I2, Z2](f: Chunk[I2] => Chunk[I], g: Z => Z2): ZSink[R, E, I2, Z2] =
    contramapChunks(f).map(g)

  /**
   * Effectfully transforms both input chunks and result of this sink using the provided functions.
   */
  def dimapChunksM[R1 <: R, E1 >: E, I2, Z2](
    f: Chunk[I2] => ZIO[R1, E1, Chunk[I]],
    g: Z => ZIO[R1, E1, Z2]
  ): ZSink[R1, E1, I2, Z2] =
    contramapChunksM(f).mapM(g)

  /**
   * Runs this sink until it yields a result, then uses that result to create another
   * sink from the provided function which will continue to run until it yields a result.
   *
   * This function essentially runs sinks in sequence.
   */
  def flatMap[R1 <: R, E1 >: E, I2 <: I, Z2](f: Z => ZSink[R1, E1, I2, Z2]): ZSink[R1, E1, I2, Z2] =
    foldCauseM(ZSink.halt(_), f)

  def foldM[R1 <: R, E2, I2 <: I, Z2](
    failure: E => ZSink[R1, E2, I2, Z2],
    success: Z => ZSink[R1, E2, I2, Z2]
  ): ZSink[R1, E2, I2, Z2] =
    foldCauseM(
      _.failureOrCause match {
        case Left(e)      => failure(e)
        case Right(cause) => ZSink.halt(cause)
      },
      success
    )

  def foldCauseM[R1 <: R, E2, I2 <: I, Z2](
    failure: Cause[E] => ZSink[R1, E2, I2, Z2],
    success: Z => ZSink[R1, E2, I2, Z2]
  ): ZSink[R1, E2, I2, Z2] =
    ZSink {
      for {
        switched     <- Ref.make(false).toManaged_
        thisPush     <- self.push
        thatPush     <- Ref.make[Push[R1, E2, I2, Z2]](_ => ZIO.unit).toManaged_
        openThatPush <- ZManaged.switchable[R1, Nothing, Push[R1, E2, I2, Z2]]
        push = (inputs: Option[Chunk[I2]]) =>
          switched.get.flatMap { alreadySwitched =>
            if (alreadySwitched)
              inputs match {
                case None =>
                  // If upstream has ended, we want to make sure that we propagate the `None`
                  // signal to the sink resulting from `f`. This will make sure that expressions like
                  // `sink1 *> ZSink.succeed("a")` work properly and do not require another push
                  // to terminate.
                  thisPush(None).catchAllCause { cause =>
                    val switchToNextPush = Cause.sequenceCauseEither(cause) match {
                      case Left(e)  => openThatPush(failure(e).push).tap(thatPush.set) <* switched.set(true)
                      case Right(z) => openThatPush(success(z).push).tap(thatPush.set) <* switched.set(true)
                    }

                    switchToNextPush.flatMap(_.apply(None))
                  }

                case is @ Some(_) =>
                  thisPush(is).catchAllCause {
                    Cause.sequenceCauseEither(_) match {
                      case Left(e)  => openThatPush(failure(e).push).flatMap(thatPush.set) *> switched.set(true)
                      case Right(z) => openThatPush(success(z).push).flatMap(thatPush.set) *> switched.set(true)
                    }
                  }
              }
            else thatPush.get.flatMap(_.apply(inputs))
          }
      } yield push
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
  def untilOutputM[R1 <: R, E1 >: E](f: Z => ZIO[R1, E1, Boolean]): ZSink[R1, E1, I, Option[Z]] =
    ZSink {
      Push.restartable(push).map {
        case (push, restart) =>
          (is: Option[Chunk[I]]) => {
            val shouldRestart =
              is match {
                case None    => false
                case Some(_) => true
              }

            push(is).catchAll {
              case Left(e) => ZIO.fail(Left(e))
              case Right(z) =>
                f(z).mapError(Left(_)) flatMap { predicateSatisfied =>
                  if (predicateSatisfied) ZIO.fail(Right(Some(z)))
                  else if (shouldRestart) restart
                  else ZIO.fail(Right(None))
                }

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
    val more: UIO[Unit]                                       = UIO.unit

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
    foldLeft(0L)((s, _) => s + 1)

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
   * Creates a sink containing the first value.
   */
  def head[I]: ZSink[Any, Nothing, I, Option[I]] =
    ZSink(ZManaged.succeed({
      case Some(ch) =>
        ch.headOption match {
          case h: Some[_] => Push.emit(h)
          case None       => Push.more
        }
      case None => Push.emit(None)
    }))

  /**
   * Creates a sink containing the last value.
   */
  def last[I]: ZSink[Any, Nothing, I, Option[I]] =
    ZSink {
      for {
        state <- Ref.make[Option[I]](None).toManaged_
        push = (is: Option[Chunk[I]]) =>
          state.get.flatMap { last =>
            is match {
              case Some(ch) =>
                ch.lastOption match {
                  case l: Some[_] => state.set(l) *> Push.more
                  case None       => Push.more
                }
              case None => Push.emit(last)
            }
          }
      } yield push
    }

  /**
   * A sink that immediately ends with the specified value.
   */
  def succeed[Z](z: Z): ZSink[Any, Nothing, Any, Z] =
    fromPush(_ => Push.emit(z))

  /**
   * A sink that effectfully folds its inputs with the provided function, termination predicate and initial state.
   */
  def foldM[R, E, I, S](z: S)(contFn: S => Boolean)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    ZSink {
      ZManaged.succeed(contFn(z)).flatMap { shouldStart =>
        if (shouldStart) {
          for {
            state <- Ref.make(z).toManaged_
            push: zio.stream.experimental.ZSink.Push[R, E, I, S] = (is: Option[Chunk[I]]) =>
              is match {
                case None => state.get.flatMap(Push.emit)
                case Some(is) => {
                  state.get
                    .flatMap(is.foldM(_)(f).mapError(Left(_)))
                    .flatMap { s =>
                      if (contFn(s))
                        state.set(s) *> Push.more
                      else
                        Push.emit(s)
                    }
                }
              }
          } yield push
        } else {
          ZManaged.succeed(_ => Push.emit(z))
        }
      }
    }

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldLeftM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    foldM[R, E, I, S](z: S)(_ => true)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S]

  /**
   * A sink that folds its inputs with the provided function, termination predicate and initial state.
   */
  def fold[I, S](z: S)(contFn: S => Boolean)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    foldM(z)(contFn)((s, i) => UIO.succeedNow(f(s, i)))

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def foldLeft[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    fold(z)(_ => true)(f)

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, Z](b: => ZIO[R, E, Z]): ZSink[R, E, Any, Z] =
    fromPush(_ => b.foldM(Push.fail, Push.emit))
}
