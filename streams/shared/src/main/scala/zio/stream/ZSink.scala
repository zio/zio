package zio.stream

import java.time.Duration

import zio._
import zio.clock.Clock
import zio.duration._

// Important notes while writing sinks and combinators:
// - What return values for sinks mean:
//   ZIO.unit - "need more values"
//   ZIO.fail((Right(z), l)) - "ended with z and emit leftover l"
//   ZIO.fail((Left(e), l)) - "failed with e and emit leftover l"
// - Result of processing of the stream using the sink must not depend on how the stream is chunked
//   (chunking-invariance)
//   stream.run(sink).either === stream.chunkN(1).run(sink).either
// - Sinks should always end when receiving a `None`. It is a defect to not end with some
//   sort of result (even a failure) when receiving a `None`.
// - Sinks can assume they will not be pushed again after emitting a value.
abstract class ZSink[-R, +E, -I, +L, +Z] private (
  val push: ZManaged[R, Nothing, ZSink.Push[R, E, I, L, Z]]
) { self =>
  import ZSink.Push

  /**
   * Operator alias for [[race]].
   */
  final def |[R1 <: R, E1 >: E, A0, I1 <: I, L1 >: L, Z1 >: Z](
    that: ZSink[R1, E1, I1, L1, Z1]
  ): ZSink[R1, E1, I1, L1, Z1] =
    self.race(that)

  /**
   * Operator alias for [[zip]].
   */
  final def <*>[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, (Z, Z1)] =
    zip(that)

  /**
   * Operator alias for [[zipPar]].
   */
  final def <&>[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1](
    that: ZSink[R1, E1, I1, L1, Z1]
  ): ZSink[R1, E1, I1, L1, (Z, Z1)] =
    self.zipPar(that)

  /**
   * Operator alias for [[zipRight]].
   */
  final def *>[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, Z1] =
    zipRight(that)

  /**
   * Operator alias for [[zipParRight]].
   */
  final def &>[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1](that: ZSink[R1, E1, I1, L1, Z1]): ZSink[R1, E1, I1, L1, Z1] =
    self.zipParRight(that)

  /**
   * Operator alias for [[zipLeft]].
   */
  final def <*[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, Z] =
    zipLeft(that)

  /**
   * Operator alias for [[zipParLeft]].
   */
  final def <&[R1 <: R, E1 >: E, I1 <: I, L1 >: L](that: ZSink[R1, E1, I1, L1, Any]): ZSink[R1, E1, I1, L1, Z] =
    self.zipParLeft(that)

  /**
   * Replaces this sink's result with the provided value.
   */
  def as[Z2](z: => Z2): ZSink[R, E, I, L, Z2] =
    map(_ => z)

  /**
   * Repeatedly runs the sink for as long as its results satisfy
   * the predicate `p`. The sink's results will be accumulated
   * using the stepping function `f`.
   */
  def collectAllWhileWith[S](z: S)(p: Z => Boolean)(f: (S, Z) => S)(implicit ev: L <:< I): ZSink[R, E, I, L, S] =
    ZSink {
      Ref.makeManaged(z).flatMap { acc =>
        Push.restartable(push).map {
          case (push, restart) =>
            def go(s: S, in: Option[Chunk[I]], end: Boolean): ZIO[R, (Either[E, S], Chunk[L]), S] =
              push(in)
                .as(s)
                .catchAll({
                  case (Left(e), leftover) => Push.fail(e, leftover)
                  case (Right(z), leftover) =>
                    if (p(z)) {
                      val s1 = f(s, z)
                      if (leftover.isEmpty)
                        if (end) Push.emit(s1, Chunk.empty) else restart.as(s1)
                      else
                        restart *> go(s1, Some(leftover.asInstanceOf[Chunk[I]]), end)
                    } else {
                      Push.emit(s, leftover)
                    }
                })

            (in: Option[Chunk[I]]) => acc.get.flatMap(s => go(s, in, in.isEmpty).flatMap(s1 => acc.set(s1)))
        }
      }
    }

  /**
   * Transforms this sink's input elements.
   */
  def contramap[I2](f: I2 => I): ZSink[R, E, I2, L, Z] =
    contramapChunks(_.map(f))

  /**
   * Effectfully transforms this sink's input elements.
   */
  def contramapM[R1 <: R, E1 >: E, I2](f: I2 => ZIO[R1, E1, I]): ZSink[R1, E1, I2, L, Z] =
    contramapChunksM(_.mapM(f))

  /**
   * Transforms this sink's input chunks.
   * `f` must preserve chunking-invariance
   */
  def contramapChunks[I2](f: Chunk[I2] => Chunk[I]): ZSink[R, E, I2, L, Z] =
    ZSink(self.push.map(push => input => push(input.map(f))))

  /**
   * Effectfully transforms this sink's input chunks.
   * `f` must preserve chunking-invariance
   */
  def contramapChunksM[R1 <: R, E1 >: E, I2](
    f: Chunk[I2] => ZIO[R1, E1, Chunk[I]]
  ): ZSink[R1, E1, I2, L, Z] =
    ZSink[R1, E1, I2, L, Z](
      self.push.map(push =>
        input =>
          input match {
            case Some(value) =>
              f(value).mapError(e => (Left(e), Chunk.empty)).flatMap((is: Chunk[I]) => push(Some(is)))
            case None => push(None)
          }
      )
    )

  /**
   * Transforms both inputs and result of this sink using the provided functions.
   */
  def dimap[I2, Z2](f: I2 => I, g: Z => Z2): ZSink[R, E, I2, L, Z2] =
    contramap(f).map(g)

  /**
   * Effectfully transforms both inputs and result of this sink using the provided functions.
   */
  def dimapM[R1 <: R, E1 >: E, I2, Z2](
    f: I2 => ZIO[R1, E1, I],
    g: Z => ZIO[R1, E1, Z2]
  ): ZSink[R1, E1, I2, L, Z2] =
    contramapM(f).mapM(g)

  /**
   * Transforms both input chunks and result of this sink using the provided functions.
   */
  def dimapChunks[I2, Z2](f: Chunk[I2] => Chunk[I], g: Z => Z2): ZSink[R, E, I2, L, Z2] =
    contramapChunks(f).map(g)

  /**
   * Effectfully transforms both input chunks and result of this sink using the provided functions.
   * `f` and `g` must preserve chunking-invariance
   */
  def dimapChunksM[R1 <: R, E1 >: E, I2, Z2](
    f: Chunk[I2] => ZIO[R1, E1, Chunk[I]],
    g: Z => ZIO[R1, E1, Z2]
  ): ZSink[R1, E1, I2, L, Z2] =
    contramapChunksM(f).mapM(g)

  /**
   * Runs this sink until it yields a result, then uses that result to create another
   * sink from the provided function which will continue to run until it yields a result.
   *
   * This function essentially runs sinks in sequence.
   */
  def flatMap[R1 <: R, E1 >: E, I2 <: I, L2, Z2](
    f: Z => ZSink[R1, E1, I2, L2, Z2]
  )(implicit ev: L <:< I2): ZSink[R1, E1, I2, L2, Z2] =
    foldM(e => ZSink.fail(e).asInstanceOf[ZSink[R1, E1, I2, L2, Z2]], f)

  def foldM[R1 <: R, E2, I2 <: I, L2, Z2](
    failure: E => ZSink[R1, E2, I2, L2, Z2],
    success: Z => ZSink[R1, E2, I2, L2, Z2]
  )(implicit ev: L <:< I2): ZSink[R1, E2, I2, L2, Z2] =
    ZSink {
      for {
        switched     <- Ref.make(false).toManaged_
        thisPush     <- self.push
        thatPush     <- Ref.make[Push[R1, E2, I2, L2, Z2]](_ => ZIO.unit).toManaged_
        openThatPush <- ZManaged.switchable[R1, Nothing, Push[R1, E2, I2, L2, Z2]]
        push = (in: Option[Chunk[I2]]) => {
          switched.get.flatMap { sw =>
            if (!sw) {
              thisPush(in).catchAll { v =>
                val leftover = v._2
                val nextSink = v._1.fold(failure, success)
                openThatPush(nextSink.push).tap(thatPush.set).flatMap { p =>
                  switched.set(true) *> {
                    if (in.isDefined)
                      p(Some(leftover).asInstanceOf[Some[Chunk[I2]]]).when(leftover.nonEmpty)
                    else
                      p(Some(leftover).asInstanceOf[Some[Chunk[I2]]]).when(leftover.nonEmpty) *> p(None)
                  }
                }
              }
            } else {
              thatPush.get.flatMap(p => p(in))
            }
          }
        }
      } yield push
    }

  /**
   * Transforms this sink's result.
   */
  def map[Z2](f: Z => Z2): ZSink[R, E, I, L, Z2] =
    ZSink(self.push.map(sink => (inputs: Option[Chunk[I]]) => sink(inputs).mapError(e => (e._1.map(f), e._2))))

  /**
   * Transforms the errors emitted by this sink using `f`.
   */
  def mapError[E2](f: E => E2): ZSink[R, E2, I, L, Z] =
    ZSink(self.push.map(p => (in: Option[Chunk[I]]) => p(in).mapError(e => (e._1.left.map(f), e._2))))

  /**
   * Effectfully transforms this sink's result.
   */
  def mapM[R1 <: R, E1 >: E, Z2](f: Z => ZIO[R1, E1, Z2]): ZSink[R1, E1, I, L, Z2] =
    ZSink(
      self.push.map(push =>
        (inputs: Option[Chunk[I]]) =>
          push(inputs).catchAll {
            case (Left(e), left)  => Push.fail(e, left)
            case (Right(z), left) => f(z).foldM(e => Push.fail(e, left), z2 => Push.emit(z2, left))
          }
      )
    )

  /**
   * Runs both sinks in parallel on the input, , returning the result or the error from the
   * one that finishes first.
   */
  final def race[R1 <: R, E1 >: E, A0, I1 <: I, L1 >: L, Z1 >: Z](
    that: ZSink[R1, E1, I1, L1, Z1]
  ): ZSink[R1, E1, I1, L1, Z1] =
    self.raceBoth(that).map(_.merge)

  /**
   * Runs both sinks in parallel on the input, returning the result or the error from the
   * one that finishes first.
   */
  final def raceBoth[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1](
    that: ZSink[R1, E1, I1, L1, Z1]
  ): ZSink[R1, E1, I1, L1, Either[Z, Z1]] =
    ZSink(for {
      p1 <- self.push
      p2 <- that.push
      push = {
        (in: Option[Chunk[I1]]) =>
          p1(in).raceWith(p2(in))(
            (res1, fib2) =>
              res1
                .foldM(
                  f => fib2.interrupt *> ZIO.halt(f.map { case (r, leftover) => (r.map(x => Left(x)), leftover) }),
                  _ => fib2.join.mapError { case (r, leftover) => (r.map(x => Right(x)), leftover) }
                ),
            (res2, fib1) =>
              res2.foldM(
                f => fib1.interrupt *> ZIO.halt(f.map { case (r, leftover) => (r.map(x => Right(x)), leftover) }),
                _ => fib1.join.mapError { case (r, leftover) => (r.map(x => Left(x)), leftover) }
              )
          )
      }
    } yield push)

  /**
   * Returns the sink that executes this one and times its execution.
   */
  final def timed: ZSink[R with Clock, E, I, L, (Z, Duration)] =
    ZSink {
      self.push.zipWith(clock.nanoTime.toManaged_) { (push, start) =>
        push(_).catchAll {
          case (Left(e), leftover)  => Push.fail(e, leftover)
          case (Right(z), leftover) => clock.nanoTime.flatMap(stop => Push.emit(z -> fromNanos(stop - start), leftover))
        }
      }
    }

  /**
   * Converts this sink to a transducer that feeds incoming elements to the sink
   * and emits the sink's results as outputs. The sink will be restarted when
   * it ends.
   */
  def toTransducer(implicit ev: L <:< I): ZTransducer[R, E, I, Z] =
    ZTransducer {
      ZSink.Push.restartable(push).map {
        case (push, restart) =>
          def go(input: Option[Chunk[I]]): ZIO[R, E, Chunk[Z]] =
            push(input).foldM(
              {
                case (Left(e), _) => ZIO.fail(e)
                case (Right(z), leftover) =>
                  restart *> {
                    if (leftover.isEmpty || input.isEmpty) {
                      ZIO.succeed(Chunk.single(z))
                    } else {
                      go(Some(leftover).asInstanceOf[Option[Chunk[I]]]).map(more => Chunk.single(z) ++ more)
                    }
                  }
              },
              _ => UIO.succeedNow(Chunk.empty)
            )

          (input: Option[Chunk[I]]) => go(input)
      }
    }

  /**
   * Feeds inputs to this sink until it yields a result, then switches over to the
   * provided sink until it yields a result, combining the two results in a tuple.
   */
  final def zip[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, (Z, Z1)] =
    zipWith(that)((_, _))

  /**
   * Like [[zip]], but keeps only the result from the `that` sink.
   */
  final def zipLeft[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, Z] =
    zipWith(that)((z, _) => z)

  /**
   * Runs both sinks in parallel on the input and combines the results in a tuple.
   */
  final def zipPar[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1](
    that: ZSink[R1, E1, I1, L1, Z1]
  ): ZSink[R1, E1, I1, L1, (Z, Z1)] =
    zipWithPar(that)((_, _))

  /**
   * Like [[zipPar]], but keeps only the result from this sink.
   */
  final def zipParLeft[R1 <: R, E1 >: E, I1 <: I, L1 >: L](
    that: ZSink[R1, E1, I1, L1, Any]
  ): ZSink[R1, E1, I1, L1, Z] =
    zipWithPar(that)((b, _) => b)

  /**
   * Like [[zipPar]], but keeps only the result from the `that` sink.
   */
  final def zipParRight[R1 <: R, E1 >: E, I1 <: I, Z1, L1 >: L](
    that: ZSink[R1, E1, I1, L1, Z1]
  ): ZSink[R1, E1, I1, L1, Z1] =
    zipWithPar(that)((_, c) => c)

  /**
   * Like [[zip]], but keeps only the result from this sink.
   */
  final def zipRight[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, Z1] =
    zipWith(that)((_, z1) => z1)

  /**
   * Feeds inputs to this sink until it yields a result, then switches over to the
   * provided sink until it yields a result, finally combining the two results with `f`.
   */
  final def zipWith[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(f: (Z, Z1) => Z2)(implicit ev: L <:< I1): ZSink[R1, E1, I1, L1, Z2] =
    flatMap(z => that.map(f(z, _)))

  /**
   * Runs both sinks in parallel on the input and combines the results
   * using the provided function.
   */
  final def zipWithPar[R1 <: R, E1 >: E, I1 <: I, L1 >: L, Z1, Z2](
    that: ZSink[R1, E1, I1, L1, Z1]
  )(f: (Z, Z1) => Z2): ZSink[R1, E1, I1, L1, Z2] = {
    sealed trait State[+Z, +Z1]
    case object BothRunning          extends State[Nothing, Nothing]
    case class LeftDone[+Z](z: Z)    extends State[Z, Nothing]
    case class RightDone[+Z1](z: Z1) extends State[Nothing, Z1]

    ZSink(for {
      ref <- ZRef.make[State[Z, Z1]](BothRunning).toManaged_
      p1  <- self.push
      p2  <- that.push
      push: Push[R1, E1, I1, L1, Z2] = {
        in =>
          ref.get.flatMap {
            state =>
              val newState: ZIO[R1, (Either[E1, Z2], Chunk[L1]), State[Z, Z1]] = {
                state match {
                  case BothRunning => {
                    val l: ZIO[R, (Either[E1, Z2], Chunk[L1]), Option[(Z, Chunk[L])]] = p1(in).foldM({
                      case (Left(e), l)  => Push.fail(e, l)
                      case (Right(z), l) => ZIO.succeedNow(Some((z, l)))
                    }, _ => ZIO.succeedNow(None))
                    val r: ZIO[R1, (Left[E1, Nothing], Chunk[L1]), Option[(Z1, Chunk[L1])]] = p2(in).foldM({
                      case (Left(e), l)  => Push.fail(e, l)
                      case (Right(z), l) => ZIO.succeedNow(Some((z, l)))
                    }, _ => ZIO.succeedNow(None))

                    l.zipPar(r).flatMap {
                      case (Some((z, l)), Some((z1, l1))) => {
                        val minLeftover = if (l.length > l1.length) l1 else l
                        ZIO.fail((Right(f(z, z1)), minLeftover))
                      }
                      case (Some((z, _)), None)  => ZIO.succeedNow(LeftDone(z))
                      case (None, Some((z1, _))) => ZIO.succeedNow(RightDone(z1))
                      case (None, None)          => ZIO.succeedNow(BothRunning)
                    }

                  }
                  case LeftDone(z) => {
                    p2(in)
                      .catchAll({
                        case (Left(e), l)    => Push.fail(e, l)
                        case (Right(z1), l1) => Push.emit(f(z, z1), l1)
                      })
                      .as(state)
                  }
                  case RightDone(z1) => {
                    p1(in)
                      .catchAll({
                        case (Left(e), l)   => Push.fail(e, l)
                        case (Right(z), l1) => Push.emit(f(z, z1), l1)
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

  def exposeLeftover: ZSink[R, E, I, Nothing, (Z, Chunk[L])] = ZSink {
    self.push.map { p => (in: Option[Chunk[I]]) =>
      p(in).mapError { case (v, leftover) => (v.map(z => (z, leftover)), Chunk.empty) }
    }
  }

  def dropLeftover: ZSink[R, E, I, Nothing, Z] = ZSink {
    self.push.map(p => (in: Option[Chunk[I]]) => p(in).mapError { case (v, _) => (v, Chunk.empty) })
  }

  /**
   * Creates a sink that produces values until one verifies
   * the predicate `f`.
   */
  def untilOutputM[R1 <: R, E1 >: E](
    f: Z => ZIO[R1, E1, Boolean]
  )(implicit ev: L <:< I): ZSink[R1, E1, I, L, Option[Z]] =
    ZSink {
      Push.restartable(push).map {
        case (push, restart) =>
          def go(in: Option[Chunk[I]], end: Boolean): ZIO[R1, (Either[E1, Option[Z]], Chunk[L]), Unit] =
            push(in).catchAll {
              case (Left(e), leftover) => Push.fail(e, leftover)
              case (Right(z), leftover) =>
                f(z).mapError(err => (Left(err), leftover)).flatMap { satisfied =>
                  if (satisfied)
                    Push.emit(Some(z), leftover)
                  else if (leftover.isEmpty)
                    if (end) Push.emit(None, Chunk.empty) else restart *> Push.more
                  else
                    go(Some(leftover.asInstanceOf[Chunk[I]]), end)
                }
            }

          (is: Option[Chunk[I]]) => go(is, is.isEmpty)
      }
    }
}

object ZSink extends ZSinkPlatformSpecificConstructors {
  type Push[-R, +E, -I, +L, +Z] = Option[Chunk[I]] => ZIO[R, (Either[E, Z], Chunk[L]), Unit]

  object Push {
    def emit[I, Z](z: Z, leftover: Chunk[I]): IO[(Right[Nothing, Z], Chunk[I]), Nothing] = IO.fail((Right(z), leftover))
    def fail[I, E](e: E, leftover: Chunk[I]): IO[(Left[E, Nothing], Chunk[I]), Nothing]  = IO.fail((Left(e), leftover))
    def halt[E](c: Cause[E]): ZIO[Any, (Left[E, Nothing], Chunk[Nothing]), Nothing] =
      IO.halt(c).mapError(e => (Left(e), Chunk.empty))
    val more: UIO[Unit] = UIO.unit

    /**
     * Decorates a Push with a ZIO value that re-initializes it with a fresh state.
     */
    def restartable[R, E, I, L, Z](
      sink: ZManaged[R, Nothing, Push[R, E, I, L, Z]]
    ): ZManaged[R, Nothing, (Push[R, E, I, L, Z], URIO[R, Unit])] =
      for {
        switchSink  <- ZManaged.switchable[R, Nothing, Push[R, E, I, L, Z]]
        initialSink <- switchSink(sink).toManaged_
        currSink    <- Ref.make(initialSink).toManaged_
        restart     = switchSink(sink).flatMap(currSink.set)
        newPush     = (input: Option[Chunk[I]]) => currSink.get.flatMap(_.apply(input))
      } yield (newPush, restart)
  }

  def apply[R, E, I, L, Z](push: ZManaged[R, Nothing, Push[R, E, I, L, Z]]) =
    new ZSink(push) {}

  /**
   * A sink that collects all of its inputs into a chunk.
   */
  def collectAll[A]: ZSink[Any, Nothing, A, Nothing, Chunk[A]] = ZSink {
    for {
      builder     <- UIO(ChunkBuilder.make[A]()).toManaged_
      foldingSink = foldLeftChunks(builder)((b, chunk: Chunk[A]) => b ++= chunk).map(_.result())
      push        <- foldingSink.push
    } yield push
  }

  /**
   * A sink that collects all of its inputs into a map. The keys are extracted from inputs
   * using the keying function `key`; if multiple inputs use the same key, they are merged
   * using the `f` function.
   */
  def collectAllToMap[A, K](key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, A, Nothing, Map[K, A]] =
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
  def collectAllToSet[A]: ZSink[Any, Nothing, A, Nothing, Set[A]] =
    foldLeftChunks(Set[A]())((acc, as) => as.foldLeft(acc)(_ + _))

  /**
   * A sink that counts the number of elements fed to it.
   */
  val count: ZSink[Any, Nothing, Any, Nothing, Long] =
    foldLeft(0L)((s, _) => s + 1)

  /**
   * Creates a sink halting with the specified `Throwable`.
   */
  def die(e: => Throwable): ZSink[Any, Nothing, Any, Nothing, Nothing] =
    ZSink.halt(Cause.die(e))

  /**
   * Creates a sink halting with the specified message, wrapped in a
   * `RuntimeException`.
   */
  def dieMessage(m: => String): ZSink[Any, Nothing, Any, Nothing, Nothing] =
    ZSink.halt(Cause.die(new RuntimeException(m)))

  /**
   * A sink that ignores its inputs.
   */
  val drain: ZSink[Any, Nothing, Any, Nothing, Unit] =
    foreach[Any, Nothing, Any](_ => ZIO.unit).dropLeftover

  /**
   * A sink that always fails with the specified error.
   */
  def fail[E, I](e: => E): ZSink[Any, E, I, I, Nothing] =
    fromPush[Any, E, I, I, Nothing] { c =>
      val leftover = c.fold[Chunk[I]](Chunk.empty)(identity)
      Push.fail(e, leftover)
    }

  /**
   * A sink that folds its inputs with the provided function, termination predicate and initial state.
   */
  def fold[I, S](z: S)(contFn: S => Boolean)(f: (S, I) => S): ZSink[Any, Nothing, I, I, S] = {
    def foldChunk(s: S, chunk: Chunk[I], idx: Int, len: Int): (S, Option[Chunk[I]]) =
      if (idx == len) {
        (s, None)
      } else {
        val s1 = f(s, chunk(idx))
        if (contFn(s1)) {
          foldChunk(s1, chunk, idx + 1, len)
        } else {
          (s1, Some(chunk.drop(idx + 1)))
        }
      }

    if (contFn(z))
      ZSink[Any, Nothing, I, I, S] {
        for {
          state <- Ref.make(z).toManaged_
          push = (is: Option[Chunk[I]]) =>
            is match {
              case None => state.get.flatMap(s => Push.emit(s, Chunk.empty))
              case Some(is) => {
                state.get.flatMap { s =>
                  val (st, l) = foldChunk(s, is, 0, is.length)
                  l match {
                    case Some(leftover) => Push.emit(st, leftover)
                    case None           => state.set(st) *> Push.more
                  }
                }
              }
            }
        } yield push
      }
    else
      ZSink.succeed(z)
  }

  /**
   * A sink that folds its input chunks with the provided function, termination predicate and initial state.
   * `contFn` condition is checked only for the initial value and at the end of processing of each chunk.
   * `f` and `contFn` must preserve chunking-invariance.
   */
  def foldChunks[I, S](z: S)(contFn: S => Boolean)(f: (S, Chunk[I]) => S): ZSink[Any, Nothing, I, I, S] =
    foldChunksM(z)(contFn)((s, is) => UIO.succeedNow(f(s, is)))

  /**
   * A sink that effectfully folds its input chunks with the provided function, termination predicate and initial state.
   * `contFn` condition is checked only for the initial value and at the end of processing of each chunk.
   * `f` and `contFn` must preserve chunking-invariance.
   */
  def foldChunksM[R, E, I, S](
    z: S
  )(contFn: S => Boolean)(f: (S, Chunk[I]) => ZIO[R, E, S]): ZSink[R, E, I, I, S] =
    if (contFn(z))
      ZSink {
        for {
          state <- Ref.make(z).toManaged_
          push = (is: Option[Chunk[I]]) =>
            is match {
              case None => state.get.flatMap(s => Push.emit(s, Chunk.empty))
              case Some(is) => {
                state.get
                  .flatMap(f(_, is).mapError(e => (Left(e), Chunk.empty)))
                  .flatMap { s =>
                    if (contFn(s))
                      state.set(s) *> Push.more
                    else
                      Push.emit(s, Chunk.empty)
                  }
              }
            }
        } yield push
      }
    else
      ZSink.succeed(z)

  /**
   * A sink that effectfully folds its inputs with the provided function, termination predicate and initial state.
   *
   * This sink may terminate in the middle of a chunk and discard the rest of it. See the discussion on the
   * ZSink class scaladoc on sinks vs. transducers.
   */
  def foldM[R, E, I, S](z: S)(contFn: S => Boolean)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, I, S] = {
    def foldChunk(s: S, chunk: Chunk[I], idx: Int, len: Int): ZIO[R, (E, Chunk[I]), (S, Option[Chunk[I]])] =
      if (idx == len) {
        ZIO.succeedNow((s, None))
      } else {
        f(s, chunk(idx)).foldM(
          e => ZIO.fail((e, chunk.drop(idx + 1))),
          s1 =>
            if (contFn(s1)) {
              foldChunk(s1, chunk, idx + 1, len)
            } else {
              ZIO.succeedNow((s1, Some(chunk.drop(idx + 1))))
            }
        )
      }

    if (contFn(z))
      ZSink[R, E, I, I, S] {
        for {
          state <- Ref.make(z).toManaged_
          push = (is: Option[Chunk[I]]) =>
            is match {
              case None => state.get.flatMap(s => Push.emit(s, Chunk.empty))
              case Some(is) => {
                state.get.flatMap { s =>
                  foldChunk(s, is, 0, is.length).foldM(err => Push.fail(err._1, err._2), {
                    case (st, l) => {
                      l match {
                        case Some(leftover) => Push.emit(st, leftover)
                        case None           => state.set(st) *> Push.more
                      }
                    }
                  })
                }
              }
            }
        } yield push
      }
    else
      ZSink.succeed(z)
  }

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def foldLeft[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, Nothing, S] =
    fold(z)(_ => true)(f).dropLeftover

  /**
   * A sink that folds its input chunks with the provided function and initial state.
   * `f` must preserve chunking-invariance.
   */
  def foldLeftChunks[I, S](z: S)(f: (S, Chunk[I]) => S): ZSink[Any, Nothing, I, Nothing, S] =
    foldChunks(z)(_ => true)(f).asInstanceOf[ZSink[Any, Nothing, I, Nothing, S]]

  /**
   * A sink that effectfully folds its input chunks with the provided function and initial state.
   * `f` must preserve chunking-invariance.
   */
  def foldLeftChunksM[R, E, I, S](z: S)(f: (S, Chunk[I]) => ZIO[R, E, S]): ZSink[R, E, I, Nothing, S] =
    foldChunksM[R, E, I, S](z: S)(_ => true)(f).dropLeftover

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldLeftM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, I, S] =
    foldM[R, E, I, S](z: S)(_ => true)(f)

  /**
   * A sink that executes the provided effectful function for every element fed to it.
   */
  def foreach[R, E, I](f: I => ZIO[R, E, Any]): ZSink[R, E, I, I, Unit] = {
    def go(chunk: Chunk[I], idx: Int, len: Int): ZIO[R, (Left[E, Nothing], Chunk[I]), Unit] =
      if (idx == len)
        Push.more
      else
        f(chunk(idx)).foldM(e => Push.fail(e, chunk.drop(idx + 1)), _ => go(chunk, idx + 1, len))

    ZSink.fromPush[R, E, I, I, Unit] {
      case Some(is) => go(is, 0, is.length)
      case None     => Push.emit((), Chunk.empty)
    }
  }

  /**
   * A sink that executes the provided effectful function for every chunk fed to it.
   */
  def foreachChunk[R, E, I](f: Chunk[I] => ZIO[R, E, Any]): ZSink[R, E, I, Nothing, Unit] =
    ZSink.fromPush[R, E, I, Nothing, Unit] {
      case Some(is) => f(is).mapError(e => (Left(e), Chunk.empty)) *> Push.more
      case None     => Push.emit((), Chunk.empty)
    }

  /**
   * A sink that executes the provided effectful function for every element fed to it
   * until `f` evaluates to `false`.
   */
  final def foreachWhile[R, E, I](f: I => ZIO[R, E, Boolean]): ZSink[R, E, I, I, Unit] = {
    def go(chunk: Chunk[I], idx: Int, len: Int): ZIO[R, (Either[E, Unit], Chunk[I]), Unit] =
      if (idx == len)
        Push.more
      else
        f(chunk(idx)).foldM(
          e => Push.fail(e, chunk.drop(idx + 1)),
          b => if (b) go(chunk, idx + 1, len) else Push.emit((), chunk.drop(idx))
        )

    ZSink.fromPush[R, E, I, I, Unit] {
      case Some(is) => go(is, 0, is.length)
      case None     => Push.emit((), Chunk.empty)
    }
  }

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, I, Z](b: => ZIO[R, E, Z]): ZSink[R, E, I, I, Z] =
    fromPush[R, E, I, I, Z] { in =>
      val leftover = in.fold[Chunk[I]](Chunk.empty)(identity)
      b.foldM(Push.fail(_, leftover), z => Push.emit(z, leftover))
    }

  def fromPush[R, E, I, L, Z](sink: Push[R, E, I, L, Z]): ZSink[R, E, I, L, Z] =
    ZSink(Managed.succeed(sink))

  /**
   * Creates a sink halting with a specified cause.
   */
  def halt[E](e: => Cause[E]): ZSink[Any, E, Any, Nothing, Nothing] =
    ZSink.fromPush[Any, E, Any, Nothing, Nothing](_ => Push.halt(e))

  /**
   * Creates a sink containing the first value.
   */
  def head[I]: ZSink[Any, Nothing, I, I, Option[I]] =
    ZSink[Any, Nothing, I, I, Option[I]](ZManaged.succeed({
      case Some(ch) =>
        if (ch.isEmpty) {
          Push.more
        } else {
          Push.emit(Some(ch.head), ch.drop(1))
        }
      case None => Push.emit(None, Chunk.empty)
    }))

  /**
   * Creates a sink containing the last value.
   */
  def last[I]: ZSink[Any, Nothing, I, Nothing, Option[I]] =
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
              case None => Push.emit(last, Chunk.empty)
            }
          }
      } yield push
    }

  /**
   * A sink that depends on another managed value
   * `resource` will be finalized after the processing.
   */
  def managed[R, E, I, A, L <: I, Z](resource: ZManaged[R, E, A])(fn: A => ZSink[R, E, I, L, Z]): ZSink[R, E, I, I, Z] =
    ZSink(resource.fold[ZSink[R, E, I, I, Z]](err => ZSink.fail[E, I](err), m => fn(m)).flatMap(_.push))

  /**
   * A sink that immediately ends with the specified value.
   */
  def succeed[I, Z](z: => Z): ZSink[Any, Nothing, I, I, Z] =
    fromPush[Any, Nothing, I, I, Z] { c =>
      val leftover = c.fold[Chunk[I]](Chunk.empty)(identity)
      Push.emit(z, leftover)
    }

  /**
   * A sink that sums incoming numeric values.
   */
  def sum[A](implicit A: Numeric[A]): ZSink[Any, Nothing, A, Nothing, A] =
    foldLeft(A.zero)(A.plus)

  /**
   * A sink with timed execution.
   */
  def timed: ZSink[Clock, Nothing, Any, Nothing, Duration] = ZSink.drain.timed.map(_._2)
}
