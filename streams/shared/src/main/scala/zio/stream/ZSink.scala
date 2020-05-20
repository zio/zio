package zio.stream

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
   * Operator alias for [[race]].
   */
  final def |[R1 <: R, E1 >: E, A0, I1 <: I, Z1 >: Z](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z1] =
    self.race(that)

  /**
   * Operator alias for [[zip]].
   */
  final def <*>[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, (Z, Z1)] =
    zip(that)

  /**
   * Operator alias for [[zipPar]].
   */
  final def <&>[R1 <: R, E1 >: E, I1 <: I, Z1](that: ZSink[R1, E1, I1, Z1]): ZSink[R1, E1, I1, (Z, Z1)] =
    self.zipPar(that)

  /**
   * Operator alias for [[zipRight]].
   */
  final def *>[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z1] =
    zipRight(that)

  /**
   * Operator alias for [[zipParRight]].
   */
  final def &>[R1 <: R, E1 >: E, I1 <: I, Z1](that: ZSink[R1, E1, I1, Z1]): ZSink[R1, E1, I1, Z1] =
    self.zipParRight(that)

  /**
   * Operator alias for [[zipLeft]].
   */
  final def <*[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z] =
    zipLeft(that)

  /**
   * Operator alias for [[zipParLeft]].
   */
  final def <&[R1 <: R, E1 >: E, I1 <: I](that: ZSink[R1, E1, I1, Any]): ZSink[R1, E1, I1, Z] = self.zipParLeft(that)

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
    self.toTransducer >>> ZSink
      .fold[Z, (S, Boolean)]((z, true))(_._2) {
        case ((st, _), v) => {
          if (p(v)) (f(st, v), true) else (st, false)
        }
      }
      .map(_._1)

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
    ZSink[R1, E1, I2, Z](
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
            if (!alreadySwitched)
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
    ZSink(self.push.map(sink => (inputs: Option[Chunk[I]]) => sink(inputs).mapError(_.map(f))))

  /**
   * Transforms the errors emitted by this sink using `f`.
   */
  def mapError[E2](f: E => E2): ZSink[R, E2, I, Z] =
    ZSink(self.push.map(p => in => p(in).mapError(e => e.left.map(f))))

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
                case Left(e)  => ZIO.fail(e)
                case Right(z) => restart.as(Chunk.single(z))
              },
              _ => UIO.succeedNow(Chunk.empty)
            )
      }
    }

  /**
   * Runs both sinks in parallel on the input, , returning the result or the error from the
   * one that finishes first.
   */
  final def race[R1 <: R, E1 >: E, A0, I1 <: I, Z1 >: Z](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z1] =
    self.raceBoth(that).map(_.merge)

  /**
   * Runs both sinks in parallel on the input, returning the result or the error from the
   * one that finishes first.
   */
  final def raceBoth[R1 <: R, E1 >: E, A0, I1 <: I, Z1](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Either[Z, Z1]] =
    ZSink(for {
      p1 <- self.push
      p2 <- that.push
      push: Push[R1, E1, I1, Either[Z, Z1]] = { in =>
        p1(in).raceWith(p2(in))(
          (res1, fib2) =>
            res1
              .foldM(f => fib2.interrupt *> ZIO.halt(f.map(_.map(Left(_)))), _ => fib2.join.mapError(_.map(Right(_)))),
          (res2, fib1) =>
            res2.foldM(f => fib1.interrupt *> ZIO.halt(f.map(_.map(Right(_)))), _ => fib1.join.mapError(_.map(Left(_))))
        )
      }
    } yield push)

  /**
   * Feeds inputs to this sink until it yields a result, then switches over to the
   * provided sink until it yields a result, combining the two results in a tuple.
   */
  final def zip[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, (Z, Z1)] =
    zipWith(that)((_, _))

  /**
   * Like [[zip]], but keeps only the result from the `that` sink.
   */
  final def zipLeft[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z] =
    zipWith(that)((z, _) => z)

  /**
   * Runs both sinks in parallel on the input and combines the results in a tuple.
   */
  final def zipPar[R1 <: R, E1 >: E, I1 <: I, Z1](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, (Z, Z1)] =
    zipWithPar(that)((_, _))

  /**
   * Like [[zipPar]], but keeps only the result from this sink.
   */
  final def zipParLeft[R1 <: R, E1 >: E, I1 <: I](
    that: ZSink[R1, E1, I1, Any]
  ): ZSink[R1, E1, I1, Z] =
    zipWithPar(that)((b, _) => b)

  /**
   * Like [[zipPar]], but keeps only the result from the `that` sink.
   */
  final def zipParRight[R1 <: R, E1 >: E, I1 <: I, Z1](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z1] =
    zipWithPar(that)((_, c) => c)

  /**
   * Like [[zip]], but keeps only the result from this sink.
   */
  final def zipRight[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  ): ZSink[R1, E1, I1, Z1] =
    zipWith(that)((_, z1) => z1)

  /**
   * Feeds inputs to this sink until it yields a result, then switches over to the
   * provided sink until it yields a result, finally combining the two results with `f`.
   */
  final def zipWith[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  )(f: (Z, Z1) => Z2): ZSink[R1, E1, I1, Z2] =
    flatMap(z => that.map(f(z, _)))

  /**
   * Runs both sinks in parallel on the input and combines the results
   * using the provided function.
   */
  final def zipWithPar[R1 <: R, E1 >: E, I1 <: I, Z1, Z2](
    that: ZSink[R1, E1, I1, Z1]
  )(f: (Z, Z1) => Z2): ZSink[R1, E1, I1, Z2] = {

    sealed trait State[+Z, +Z1]
    case object BothRunning          extends State[Nothing, Nothing]
    case class LeftDone[+Z](z: Z)    extends State[Z, Nothing]
    case class RightDone[+Z1](z: Z1) extends State[Nothing, Z1]

    ZSink(for {
      ref <- ZRef.make[State[Z, Z1]](BothRunning).toManaged_
      p1  <- self.push
      p2  <- that.push
      push: Push[R1, E1, I1, Z2] = {
        in =>
          ref.get.flatMap {
            state =>
              val newState: ZIO[R1, Either[E1, Z2], State[Z, Z1]] = {
                state match {
                  case BothRunning => {
                    val l = p1(in).foldM({
                      case Left(e)  => ZIO.fail(Left(e))
                      case Right(z) => ZIO.succeedNow(Some(z))
                    }, _ => ZIO.succeedNow(None))
                    val r = p2(in).foldM({
                      case Left(e)  => ZIO.fail(Left(e))
                      case Right(z) => ZIO.succeedNow(Some(z))
                    }, _ => ZIO.succeedNow(None))

                    l.zipPar(r).flatMap {
                      case (Some(z), Some(z1)) => ZIO.fail(Right(f(z, z1)))
                      case (Some(z), None)     => ZIO.succeedNow(LeftDone(z))
                      case (None, Some(z1))    => ZIO.succeedNow(RightDone(z1))
                      case (None, None)        => ZIO.succeedNow(BothRunning)
                    }

                  }
                  case LeftDone(z) => {
                    p2(in)
                      .catchAll({
                        case Left(e)   => ZIO.fail(Left(e))
                        case Right(z1) => ZIO.fail(Right(f(z, z1)))
                      })
                      .as(state)
                  }
                  case RightDone(z1) => {
                    p1(in)
                      .catchAll({
                        case Left(e)  => ZIO.fail(Left(e))
                        case Right(z) => ZIO.fail(Right(f(z, z1)))
                      })
                      .as(state)
                  }
                }
              }
              newState.flatMap(ns => if (ns eq state) ZIO.unit else ref.set(ns))
          }
      }
    } yield push)
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

object ZSink extends ZSinkPlatformSpecificConstructors {
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
    foldLeftChunks(Chunk[A]())(_ ++ (_: Chunk[A])).map(_.toList)

  /**
   * A sink that collects all of its inputs into a map. The keys are extracted from inputs
   * using the keying function `key`; if multiple inputs use the same key, they are merged
   * using the `f` function.
   */
  def collectAllToMap[A, K](key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, A, Map[K, A]] =
    foldLeftChunks(Map[K, A]()) { (acc, as) =>
      as.foldLeft(acc) { (acc, a) =>
        val k = key(a)

        acc.updated(
          k,
          // Avoiding `get/getOrElse` here to avoid an Option allocation
          if (acc.contains(k)) f(acc(k), a)
          else a
        )
      }
    }

  /**
   * A sink that collects all of its inputs into a set.
   */
  def collectAllToSet[A]: ZSink[Any, Nothing, A, Set[A]] =
    foldLeftChunks(Set[A]())((acc, as) => as.foldLeft(acc)(_ + _))

  /**
   * A sink that counts the number of elements fed to it.
   */
  val count: ZSink[Any, Nothing, Any, Long] =
    foldLeft(0L)((s, _) => s + 1)

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

  /**
   * A sink that ignores its inputs.
   */
  val drain: ZSink[Any, Nothing, Any, Unit] =
    foreach(_ => ZIO.unit)

  /**
   * A sink that always fails with the specified error.
   */
  def fail[E](e: => E): ZSink[Any, E, Any, Nothing] =
    halt(Cause.fail(e))

  /**
   * A sink that folds its inputs with the provided function, termination predicate and initial state.
   */
  def fold[I, S](z: S)(contFn: S => Boolean)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    foldChunks(z)(contFn)((s, is) => is.foldWhile(s)(contFn)(f))

  /**
   * A sink that folds its input chunks with the provided function, termination predicate and initial state.
   */
  def foldChunks[I, S](z: S)(contFn: S => Boolean)(f: (S, Chunk[I]) => S): ZSink[Any, Nothing, I, S] =
    foldChunksM(z)(contFn)((s, is) => UIO.succeedNow(f(s, is)))

  /**
   * A sink that effectfully folds its input chunks with the provided function, termination predicate and initial state.
   *
   * This sink may terminate in the middle of a chunk and discard the rest of it. See the discussion on the
   * ZSink class scaladoc on sinks vs. transducers.
   */
  def foldChunksM[R, E, I, S](z: S)(contFn: S => Boolean)(f: (S, Chunk[I]) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    if (contFn(z))
      ZSink {
        for {
          state <- Ref.make(z).toManaged_
          push = (is: Option[Chunk[I]]) =>
            is match {
              case None => state.get.flatMap(Push.emit)
              case Some(is) => {
                state.get
                  .flatMap(f(_, is).mapError(Left(_)))
                  .flatMap { s =>
                    if (contFn(s))
                      state.set(s) *> Push.more
                    else
                      Push.emit(s)
                  }
              }
            }
        } yield push
      }
    else
      ZSink(ZManaged.succeed(_ => Push.emit(z)))

  /**
   * A sink that effectfully folds its inputs with the provided function, termination predicate and initial state.
   *
   * This sink may terminate in the middle of a chunk and discard the rest of it. See the discussion on the
   * ZSink class scaladoc on sinks vs. transducers.
   */
  def foldM[R, E, I, S](z: S)(contFn: S => Boolean)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    foldChunksM(z)(contFn)((s, is) => is.foldWhileM(s)(contFn)(f))

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def foldLeft[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    fold(z)(_ => true)(f)

  /**
   * A sink that folds its input chunks with the provided function and initial state.
   */
  def foldLeftChunks[I, S](z: S)(f: (S, Chunk[I]) => S): ZSink[Any, Nothing, I, S] =
    foldChunks(z)(_ => true)(f)

  /**
   * A sink that effectfully folds its input chunks with the provided function and initial state.
   */
  def foldLeftChunksM[R, E, I, S](z: S)(f: (S, Chunk[I]) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    foldChunksM[R, E, I, S](z: S)(_ => true)(f)

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldLeftM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    foldM[R, E, I, S](z: S)(_ => true)(f)

  /**
   * A sink that executes the provided effectful function for every element fed to it.
   */
  def foreach[R, E, I](f: I => ZIO[R, E, Any]): ZSink[R, E, I, Unit] =
    ZSink.fromPush {
      case Some(is) => is.mapM_(f).mapError(Left(_))
      case None     => ZIO.fail(Right(()))
    }

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, Z](b: => ZIO[R, E, Z]): ZSink[R, E, Any, Z] =
    fromPush(_ => b.foldM(Push.fail, Push.emit))

  def fromPush[R, E, I, Z](sink: Push[R, E, I, Z]): ZSink[R, E, I, Z] =
    ZSink(Managed.succeed(sink))

  /**
   * Creates a sink halting with a specified cause.
   */
  def halt[E](e: => Cause[E]): ZSink[Any, E, Any, Nothing] =
    ZSink.fromPush(_ => Push.halt(e))

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
  def succeed[Z](z: => Z): ZSink[Any, Nothing, Any, Z] =
    fromPush(_ => Push.emit(z))

  /**
   * A sink that sums incoming numeric values.
   */
  def sum[A](implicit A: Numeric[A]): ZSink[Any, Nothing, A, A] =
    foldLeft(A.zero)(A.plus)
}
