package zio.stream.experimental

import zio._
import zio.stm.TQueue

abstract class ZStream[-R, +E, +O](
  val process: ZManaged[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
) extends ZConduit[R, E, Unit, O, Unit](
      process.map(pull => _ => pull.mapError(_.fold[Either[E, Unit]](Right(()))(Left(_))))
    ) { self =>

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def <*>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] =
    (self crossWith that)((_, _))

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements
   * and keeps only the elements from the `this` stream. The `that` stream would be run multiple
   * times, for every element in the `this` stream.
   *
   * See also [[ZStream#zipWith]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def <*[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O] =
    (self crossWith that)((o, _) => o)

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements
   * and keeps only the elements from the `that` stream. The `that` stream would be run multiple
   * times, for every element in the `this` stream.
   *
   * See also [[ZStream#zipWith]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def *>[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O2] =
    (self crossWith that)((_, o2) => o2)

  /**
   * Symbolic alias for [[ZStream#concat]].
   */
  def ++[R1 <: R, E1 >: E, O1 >: O](that: => ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    self concat that

  /**
   * Maps the success values of this stream to the specified constant value.
   */
  def as[O2](o2: => O2): ZStream[R, E, O2] =
    map(new ZIO.ConstFn(() => o2))

  /**
   * Returns a stream whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  def bimap[E1, O1](f: E => E1, g: O => O1)(implicit ev: CanFail[E]): ZStream[R, E1, O1] =
    mapError(f).map(g)

  /**
   * Performs a filter and map in a single step.
   */
  def collect[O1](pf: PartialFunction[O, O1]): ZStream[R, E, O1] =
    ZStream(self.process.map(_.map(_.collect(pf))))

  /**
   * Performs an effectful filter and map in a single step.
   */
  final def collectM[R1 <: R, E1 >: E, O1](pf: PartialFunction[O, ZIO[R1, E1, O1]]): ZStream[R1, E1, O1] =
    ZStream(self.process.map(_.flatMap(_.collectM(pf).mapError(Some(_)))))

  /**
   * Combines the chunks from this stream and the specified stream by repeatedly applying the
   * function `f0` to extract a chunk using both sides and conceptually "offer"
   * it to the destination stream. `f0` can maintain some internal state to control
   * the combining process, with the initial state being specified by `s1`.
   */
  final def combineChunks[R1 <: R, E1 >: E, S1, O2, O3](that: ZStream[R1, E1, O2])(s1: S1)(
    f0: (
      S1,
      ZIO[R, Option[E], Chunk[O]],
      ZIO[R1, Option[E1], Chunk[O2]]
    ) => ZIO[R1, Nothing, (S1, Exit[Option[E1], Chunk[O3]])]
  ): ZStream[R1, E1, O3] =
    ZStream[R1, E1, O3] {
      for {
        left  <- self.process
        right <- that.process
        pull <- ZStream
                 .unfoldChunkM(s1) { s1 =>
                   f0(s1, left, right).flatMap {
                     case (s1, exit) =>
                       ZIO.done(
                         exit.fold(
                           Cause
                             .sequenceCauseOption(_) match {
                             case None    => Exit.succeed(None)
                             case Some(e) => Exit.halt(e)
                           },
                           chunk => Exit.succeed(Some(chunk -> s1))
                         )
                       )
                   }
                 }
                 .process
      } yield pull
    }

  /**
   * Concatenates the specified stream with this stream, resulting in a stream
   * that emits the elements from this stream and then the elements from the specified stream.
   */
  def concat[R1 <: R, E1 >: E, O1 >: O](that: => ZStream[R1, E1, O1]): ZStream[R1, E1, O1] =
    ZStream {
      // This implementation is identical to ZStream.concatAll, but specialized so we can
      // maintain laziness on `that`. Laziness on concatenation is important for combinators
      // such as `forever`.
      for {
        currStream   <- Ref.make[ZIO[R1, Option[E1], Chunk[O1]]](ZIO.fail(None)).toManaged_
        switchStream <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E1], Chunk[O1]]]
        switched     <- Ref.make(false).toManaged_
        _            <- switchStream(self.process).flatMap(currStream.set).toManaged_
        pull = {
          def go: ZIO[R1, Option[E1], Chunk[O1]] =
            currStream.get.flatten.catchAllCause {
              Cause.sequenceCauseOption(_) match {
                case Some(e) => ZIO.halt(e.map(Some(_)))
                case None =>
                  switched.getAndSet(true).flatMap {
                    if (_) ZIO.fail(None)
                    else switchStream(that.process).flatMap(currStream.set) *> go
                  }
              }
            }

          go
        }
      } yield pull
    }

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements
   * with a specified function.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def crossWith[R1 <: R, E1 >: E, O2, C](that: ZStream[R1, E1, O2])(f: (O, O2) => C): ZStream[R1, E1, C] =
    self.flatMap(l => that.map(r => f(l, r)))

  /**
   * Composes this stream with the specified stream to create a cartesian product of elements.
   * The `that` stream would be run multiple times, for every element in the `this` stream.
   *
   * See also [[ZStream#zip]] and [[ZStream#<&>]] for the more common point-wise variant.
   */
  final def cross[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] =
    (self crossWith that)((_, _))

  /**
   * Converts this stream to a stream that executes its effects but emits no
   * elements. Useful for sequencing effects using streams:
   *
   * {{{
   * (Stream(1, 2, 3).tap(i => ZIO(println(i))) ++
   *   Stream.fromEffect(ZIO(println("Done!"))).drain ++
   *   Stream(4, 5, 6).tap(i => ZIO(println(i)))).run(Sink.drain)
   * }}}
   */
  final def drain: ZStream[R, E, Nothing] =
    ZStream(self.process.map(_.forever))

  /**
   * Executes the provided finalizer after this stream's finalizers run.
   */
  final def ensuring[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, O] =
    ZStream(self.process.ensuring(fin))

  /**
   * Executes the provided finalizer before this stream's finalizers run.
   */
  final def ensuringFirst[R1 <: R](fin: ZIO[R1, Nothing, Any]): ZStream[R1, E, O] =
    ZStream(self.process.ensuringFirst(fin))

  /**
   * Repeats this stream forever.
   */
  def forever: ZStream[R, E, O] =
    self ++ forever

  /**
   * Filters the elements emitted by this stream using the provided function.
   */
  def filter(f: O => Boolean): ZStream[R, E, O] =
    ZStream(self.process.map(_.map(_.filter(f))))

  /**
   * Effectfully filters the elements emitted by this stream.
   */
  def filterM[R1 <: R, E1 >: E](f: O => ZIO[R1, E1, Boolean]): ZStream[R1, E1, O] =
    ZStream(self.process.map(_.flatMap(_.filterM(f).mapError(Some(_)))))

  /**
   * Filters this stream by the specified predicate, removing all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot(pred: O => Boolean): ZStream[R, E, O] = filter(a => !pred(a))

  /**
   * Flattens this stream-of-streams into a stream made of the concatenation in
   * strict order of all the streams.
   */
  def flatten[R1 <: R, E1 >: E, O1](implicit ev: O <:< ZStream[R1, E1, O1]) = flatMap(ev(_))

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f0`
   */
  def flatMap[R1 <: R, E1 >: E, O2](f0: O => ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = {
    def go(
      outerStream: ZIO[R1, Option[E1], Chunk[O]],
      switchInner: ZManaged[R1, Nothing, ZIO[R1, Option[E1], Chunk[O2]]] => ZIO[
        R1,
        Nothing,
        ZIO[R1, Option[E1], Chunk[O2]]
      ],
      currInnerStream: Ref[ZIO[R1, Option[E1], Chunk[O2]]]
    ): ZIO[R1, Option[E1], Chunk[O2]] = {
      def pullOuter: ZIO[R1, Option[E1], Unit] =
        outerStream
          .flatMap(os => switchInner(ZStream.concatAll(os.map(f0)).process))
          .flatMap(currInnerStream.set)

      currInnerStream.get.flatten.catchAllCause { c =>
        Cause.sequenceCauseOption(c) match {
          case Some(e) => ZIO.halt(e.map(Some(_)))
          case None    =>
            // The additional switch is needed to eagerly run the finalizer
            // *before* pulling another element from the outer stream.
            switchInner(ZManaged.succeed(ZIO.fail(None))) *>
              pullOuter *>
              go(outerStream, switchInner, currInnerStream)
        }
      }
    }

    ZStream {
      for {
        currInnerStream <- Ref.make[ZIO[R1, Option[E1], Chunk[O2]]](ZIO.fail(None)).toManaged_
        switchInner     <- ZManaged.switchable[R1, Nothing, ZIO[R1, Option[E1], Chunk[O2]]]
        outerStream     <- self.process
      } yield go(outerStream, switchInner, currInnerStream)
    }
  }

  /**
   * Halts the evaluation of this stream when the provided promise resolves.
   *
   * If the promise completes with a failure, the stream will emit that failure.
   */
  final def haltWhen[E1 >: E](p: Promise[E1, _]): ZStream[R, E1, O] =
    ZStream {
      for {
        as   <- self.process
        done <- Ref.make(false).toManaged_
        pull = done.get flatMap {
          if (_) ZIO.fail(None)
          else
            p.poll.flatMap {
              case None    => as
              case Some(v) => done.set(true) *> v.mapError(Some(_)) *> ZIO.fail(None)
            }
        }
      } yield pull
    }

  /**
   * Interrupts the evaluation of this stream when the provided promise resolves. This
   * combinator will also interrupt any in-progress element being pulled from upstream.
   *
   * If the promise completes with a failure, the stream will emit that failure.
   */
  final def interruptWhen[E1 >: E](p: Promise[E1, _]): ZStream[R, E1, O] =
    ZStream {
      for {
        as   <- self.process
        done <- Ref.make(false).toManaged_
        pull = done.get flatMap {
          if (_) ZIO.fail(None)
          else
            as.raceFirst(
              p.await
                .mapError(Some(_))
                .foldCauseM(
                  c => done.set(true) *> ZIO.halt(c),
                  _ => done.set(true) *> ZIO.fail(None)
                )
            )
        }
      } yield pull
    }

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[O2](f: O => O2): ZStream[R, E, O2] =
    ZStream(self.process.map(_.map(_.map(f))))

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  def mapAccum[S, O1](s: S)(f: (S, O) => (S, O1)): ZStream[R, E, O1] =
    mapAccumM(s)((s, a) => UIO.succeedNow(f(s, a)))

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  final def mapAccumM[R1 <: R, E1 >: E, S, O1](s: S)(f: (S, O) => ZIO[R1, E1, (S, O1)]): ZStream[R1, E1, O1] =
    ZStream {
      for {
        state <- Ref.make(s).toManaged_
        pull  <- self.process
      } yield pull.flatMap { as =>
        (for {
          s <- state.get
          t <- as.mapAccumM(s)(f)
          _ <- state.set(t._1)
        } yield t._2).mapError(Some(_))
      }
    }

  def mapConcat[O2](f: O => Iterable[O2]): ZStream[R, E, O2] =
    mapConcatChunk(o => Chunk.fromIterable(f(o)))

  def mapConcatChunk[O2](f: O => Chunk[O2]): ZStream[R, E, O2] =
    ZStream(self.process.map(_.map(_.flatMap(f))))

  /**
   * Transforms the errors emitted by this stream using `f`.
   */
  def mapError[E2](f: E => E2): ZStream[R, E2, O] =
    ZStream(self.process.map(_.mapError(_.map(f))))

  /**
   * Transforms the full causes of failures emitted by this stream.
   */
  def mapErrorCause[E2](f: Cause[E] => Cause[E2]): ZStream[R, E2, O] =
    ZStream(
      self.process.map(
        _.mapErrorCause(
          Cause.sequenceCauseOption(_) match {
            case None    => Cause.fail(None)
            case Some(c) => f(c).map(Some(_))
          }
        )
      )
    )

  /**
   * Applies the transducer to the stream and emits its outputs.
   */
  def transduce[R1 <: R, E1 >: E, O2 >: O, O3](transducer: ZTransducer[R1, E1, O2, O3]): ZStream[R1, E1, O3] =
    ZStream {
      for {
        pushTransducer <- transducer.push.map(push =>
                           (input: Option[Chunk[O2]]) => push(input).mapError(_.fold(Some(_), _ => None))
                         )
        pullSelf <- self.process
        pull = pullSelf.foldM(
          {
            case l @ Some(_) => ZIO.fail(l)
            case None        => pushTransducer(None)
          },
          os => pushTransducer(Some(os))
        )
      } yield pull
    }

  /**
   * Zips this stream with another point-wise, but keeps only the outputs of this stream.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipLeft[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O] = zipWith(that)((o, _) => o)

  /**
   * Zips this stream with another point-wise, but keeps only the outputs of the other stream.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, O2] = zipWith(that)((_, o2) => o2)

  /**
   * Zips this stream with another point-wise and emits tuples of elements from both streams.
   *
   * The new stream will end when one of the sides ends.
   */
  def zip[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2]): ZStream[R1, E1, (O, O2)] = zipWith(that)((_, _))

  /**
   * Zips this stream with another point-wise and applies the function to the paired elements.
   *
   * The new stream will end when one of the sides ends.
   */
  def zipWith[R1 <: R, E1 >: E, O2, O3](that: ZStream[R1, E1, O2])(f: (O, O2) => O3): ZStream[R1, E1, O3] = {
    def zipSides(cl: Chunk[O], cr: Chunk[O2]) =
      if (cl.size > cr.size) (cl.take(cr.size).zipWith(cr)(f), cl.drop(cr.size), Chunk())
      else if (cl.size == cr.size) (cl.zipWith(cr)(f), Chunk(), Chunk())
      else (cl.zipWith(cr.take(cl.size))(f), Chunk(), cr.drop(cl.size))

    combineChunks(that)((Chunk[O](), Chunk[O2]())) {
      case (s @ (excessL, excessR), pullL, pullR) =>
        pullL.optional
          .zipWithPar(pullR.optional) {
            case (Some(o), Some(o2)) =>
              val (emit, el, er) = zipSides(excessL ++ o, excessR ++ o2)
              (el, er) -> Exit.succeed(emit)
            case _ =>
              s -> Exit.fail(None)
          }
          .catchAllCause(e => UIO.succeed(s -> Exit.halt(e.map(Some(_)))))
    }
  }

  /**
   * Zips this stream with another point-wise, creating a new stream of pairs of elements
   * from both sides.
   *
   * The defaults `defaultLeft` and `defaultRight` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   */
  def zipAll[R1 <: R, E1 >: E, O1 >: O, O2](
    that: ZStream[R1, E1, O2]
  )(defaultLeft: O1, defaultRight: O2): ZStream[R1, E1, (O1, O2)] =
    zipAllWith(that)((_, defaultRight), (defaultLeft, _))((_, _))

  /**
   * Zips this stream with another point-wise, and keeps only elements from this stream.
   *
   * The provided default value will be used if the other stream ends before this one.
   */
  def zipAllLeft[R1 <: R, E1 >: E, O1 >: O, O2](that: ZStream[R1, E1, O2])(default: O1): ZStream[R1, E1, O1] =
    zipAllWith(that)(identity, _ => default)((o, _) => o)

  /**
   * Zips this stream with another point-wise, and keeps only elements from the other stream.
   *
   * The provided default value will be used if this stream ends before the other one.
   */
  def zipAllRight[R1 <: R, E1 >: E, O2](that: ZStream[R1, E1, O2])(default: O2): ZStream[R1, E1, O2] =
    zipAllWith(that)(_ => default, identity)((_, o2) => o2)

  /**
   * Zips this stream with another point-wise. The provided functions will be used to create elements
   * for the composed stream.

   * The functions `left` and `right` will be used if the streams have different lengths
   * and one of the streams has ended before the other.
   */
  def zipAllWith[R1 <: R, E1 >: E, O2, O3](
    that: ZStream[R1, E1, O2]
  )(left: O => O3, right: O2 => O3)(both: (O, O2) => O3): ZStream[R1, E1, O3] = {
    sealed trait State
    case class Running(excessL: Chunk[O], excessR: Chunk[O2])   extends State
    case class LeftDone(excessL: Chunk[O], excessR: Chunk[O2])  extends State
    case class RightDone(excessL: Chunk[O], excessR: Chunk[O2]) extends State
    case object End                                             extends State

    def zipSides(cl: Chunk[O], cr: Chunk[O2], bothDone: Boolean) =
      if (cl.size > cr.size) {
        if (bothDone) (cl.take(cr.size).zipWith(cr)(both) ++ cl.drop(cr.size).map(left), Chunk(), Chunk())
        else (cl.take(cr.size).zipWith(cr)(both), cl.drop(cr.size), Chunk())
      } else if (cl.size == cr.size) (cl.zipWith(cr)(both), Chunk(), Chunk())
      else {
        if (bothDone) (cl.zipWith(cr.take(cl.size))(both) ++ cr.drop(cl.size).map(right), Chunk(), Chunk())
        else (cl.zipWith(cr.take(cl.size))(both), Chunk(), cr.drop(cl.size))
      }

    def handleSuccess(maybeO: Option[Chunk[O]], maybeO2: Option[Chunk[O2]], excessL: Chunk[O], excessR: Chunk[O2]) =
      (maybeO, maybeO2) match {
        case (Some(o), Some(o2)) =>
          val (emit, el, er) = zipSides(excessL ++ o, excessR ++ o2, bothDone = false)
          Running(el, er) -> Exit.succeed(emit)

        case (None, Some(o2)) =>
          val (emit, el, er) = zipSides(excessL, excessR ++ o2, bothDone = false)
          LeftDone(el, er) -> Exit.succeed(emit)

        case (Some(o), None) =>
          val (emit, el, er) = zipSides(excessL ++ o, excessR, bothDone = false)
          RightDone(el, er) -> Exit.succeed(emit)

        case (None, None) =>
          val (emit, _, _) = zipSides(excessL, excessR, bothDone = true)
          End -> Exit.succeed(emit)
      }

    combineChunks(that)(Running(Chunk(), Chunk()): State) {
      case (Running(excessL, excessR), pullL, pullR) =>
        pullL.optional
          .zipWithPar(pullR.optional)(handleSuccess(_, _, excessL, excessR))
          .catchAllCause(e => UIO.succeed(End -> Exit.halt(e.map(Some(_)))))

      case (LeftDone(excessL, excessR), _, pullR) =>
        pullR.optional
          .map(handleSuccess(None, _, excessL, excessR))
          .catchAllCause(e => UIO.succeed(End -> Exit.halt(e.map(Some(_)))))

      case (RightDone(excessL, excessR), pullL, _) =>
        pullL.optional
          .map(handleSuccess(_, None, excessL, excessR))
          .catchAllCause(e => UIO.succeed(End -> Exit.halt(e.map(Some(_)))))

      case (End, _, _) => UIO.succeed(End -> Exit.fail(None))
    }
  }
}

object ZStream {
  def apply[R, E, O](
    process: ZManaged[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
  ): ZStream[R, E, O] =
    new ZStream(process) {}

  /**
   * Creates a pure stream from a variable list of values
   */
  def apply[A](as: A*): ZStream[Any, Nothing, A] = fromIterable(as)

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  def bracket[R, E, A](acquire: ZIO[R, E, A])(release: A => ZIO[R, Nothing, Any]): ZStream[R, E, A] =
    managed(ZManaged.make(acquire)(release))

  /**
   * Creates a stream from a single value that will get cleaned up after the
   * stream is consumed
   */
  def bracketExit[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R, Nothing, Any]): ZStream[R, E, A] =
    managed(ZManaged.makeExit(acquire)(release))

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent streams would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C]*]] for the more common point-wise variant.
   */
  def crossN[R, E, A, B, C](zStream1: ZStream[R, E, A], zStream2: ZStream[R, E, B])(
    f: (A, B) => C
  ): ZStream[R, E, C] =
    zStream1.crossWith(zStream2)(f)

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent stream would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C,D]*]] for the more common point-wise variant.
   */
  def crossN[R, E, A, B, C, D](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C]
  )(
    f: (A, B, C) => D
  ): ZStream[R, E, D] =
    for {
      a <- zStream1
      b <- zStream2
      c <- zStream3
    } yield f(a, b, c)

  /**
   * Composes the specified streams to create a cartesian product of elements
   * with a specified function. Subsequent stream would be run multiple times,
   * for every combination of elements in the prior streams.
   *
   * See also [[ZStream#zipN[R,E,A,B,C,D,F]*]] for the more common point-wise variant.
   */
  def crossN[R, E, A, B, C, D, F](
    zStream1: ZStream[R, E, A],
    zStream2: ZStream[R, E, B],
    zStream3: ZStream[R, E, C],
    zStream4: ZStream[R, E, D]
  )(
    f: (A, B, C, D) => F
  ): ZStream[R, E, F] =
    for {
      a <- zStream1
      b <- zStream2
      c <- zStream3
      d <- zStream4
    } yield f(a, b, c, d)

  /**
   * Concatenates all of the streams in the chunk to one stream.
   */
  def concatAll[R, E, O](streams: Chunk[ZStream[R, E, O]]): ZStream[R, E, O] =
    ZStream {
      val chunkSize = streams.size

      for {
        currIndex    <- Ref.make(0).toManaged_
        currStream   <- Ref.make[ZIO[R, Option[E], Chunk[O]]](ZIO.fail(None)).toManaged_
        switchStream <- ZManaged.switchable[R, Nothing, ZIO[R, Option[E], Chunk[O]]]
        pull = {
          def go: ZIO[R, Option[E], Chunk[O]] =
            currStream.get.flatten.catchAllCause {
              Cause.sequenceCauseOption(_) match {
                case Some(e) => ZIO.halt(e.map(Some(_)))
                case None =>
                  currIndex.getAndUpdate(_ + 1).flatMap { i =>
                    if (i >= chunkSize) ZIO.fail(None)
                    else switchStream(streams(i).process).flatMap(currStream.set) *> go
                  }
              }
            }

          go
        }
      } yield pull
    }

  /**
   * The stream that always dies with the `ex`.
   */
  def die(ex: => Throwable): ZStream[Any, Nothing, Nothing] =
    halt(Cause.die(ex))

  /**
   * The stream that always dies with an exception described by `msg`.
   */
  def dieMessage(msg: => String): ZStream[Any, Nothing, Nothing] =
    halt(Cause.die(new RuntimeException(msg)))

  /**
   * The empty stream
   */
  val empty: ZStream[Any, Nothing, Nothing] =
    ZStream(ZManaged.succeedNow(ZIO.fail(None)))

  /**
   * Accesses the whole environment of the stream.
   */
  def environment[R]: ZStream[R, Nothing, R] =
    fromEffect(ZIO.environment[R])

  /**
   * The stream that always fails with the `error`
   */
  def fail[E](error: => E): ZStream[Any, E, Nothing] =
    ZStream(ZManaged.succeedNow(ZIO.fail(Some(error))))

  /**
   * Creates an empty stream that never fails and executes the finalizer when it ends.
   */
  def finalizer[R](finalizer: ZIO[R, Nothing, Any]): ZStream[R, Nothing, Nothing] =
    ZStream {
      for {
        finalizerRef <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull         = (finalizerRef.add(_ => finalizer) *> ZIO.fail(None)).uninterruptible
      } yield pull
    }

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   *
   * @tparam A the value type
   * @param c a chunk of values
   * @return a finite stream of values
   */
  def fromChunk[O](c: => Chunk[O]): ZStream[Any, Nothing, O] =
    ZStream {
      for {
        doneRef <- Ref.make(false).toManaged_
        pull = doneRef.modify {
          if (_) ZIO.fail(None) -> true
          else ZIO.succeed(c)   -> true
        }.flatten
      } yield pull
    }

  /**
   * Creates a stream from an effect producing a value of type `A`
   */
  def fromEffect[R, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] =
    ZStream {
      for {
        doneRef <- Ref.make(false).toManaged_
        pull = doneRef.modify {
          if (_) ZIO.fail(None)                    -> true
          else fa.bimap(Some(_), Chunk.succeed(_)) -> true
        }.flatten
      } yield pull
    }

  /**
   * Creates a stream from an effect producing a value of type `A` or an empty Stream
   */
  def fromEffectOption[R, E, A](fa: ZIO[R, Option[E], A]): ZStream[R, E, A] =
    ZStream.unwrap {
      fa.fold(_.fold[ZStream[Any, E, Nothing]](ZStream.empty)(ZStream.fail(_)), ZStream.succeed(_))
    }

  /**
   * Creates a stream from an iterable collection of values
   */
  def fromIterable[O](as: => Iterable[O]): ZStream[Any, Nothing, O] =
    fromChunk(Chunk.fromIterable(as))

  /**
   * Creates a stream from an effect producing a value of type `Iterable[A]`
   */
  def fromIterableM[R, E, O](iterable: ZIO[R, E, Iterable[O]]): ZStream[R, E, O] =
    fromEffect(iterable).mapConcat(identity)

  /**
   * Creates a stream from a [[zio.ZQueue]] of values
   */
  def fromQueue[R, E, A](queue: ZQueue[Nothing, Any, R, E, Nothing, A]): ZStream[R, E, A] =
    ZStream {
      ZManaged.succeedNow {
        queue.take
          .map(Chunk.single)
          .catchAllCause(c =>
            queue.isShutdown.flatMap { down =>
              if (down && c.interrupted) ZIO.fail(None)
              else ZIO.halt(c.map(Some(_)))
            }
          )
      }
    }

  /**
   * Creates a stream from a [[zio.ZQueue]] of values. The queue will be shutdown once the stream is closed.
   */
  def fromQueueWithShutdown[R, E, A](queue: ZQueue[Nothing, Any, R, E, Nothing, A]): ZStream[R, E, A] =
    fromQueue(queue).ensuringFirst(queue.shutdown)

  /**
   * Creates a stream from a [[zio.Schedule]] that does not require any further
   * input. The stream will emit an element for each value output from the
   * schedule, continuing for as long as the schedule continues.
   */
  def fromSchedule[R, A](schedule: Schedule[R, Any, A]): ZStream[R, Nothing, A] =
    ZStream.fromEffect(schedule.initial).flatMap { s =>
      ZStream.succeed(schedule.extract((), s)) ++
        ZStream.unfoldM(s)(s => schedule.update((), s).map(s => (schedule.extract((), s), s)).option)
    }

  /**
   * Creates a stream from a [[zio.stm.TQueue]] of values.
   */
  def fromTQueue[A](queue: TQueue[A]): ZStream[Any, Nothing, A] =
    ZStream.repeatEffect(queue.take.commit)

  /**
   * The stream that always halts with `cause`.
   */
  def halt[E](cause: => Cause[E]): ZStream[Any, E, Nothing] =
    fromEffect(ZIO.halt(cause))

  /**
   * The infinite stream of iterative function application: a, f(a), f(f(a)), f(f(f(a))), ...
   */
  def iterate[A](a: A)(f: A => A): ZStream[Any, Nothing, A] =
    ZStream(Ref.make(a).toManaged_.map(_.getAndUpdate(f).map(Chunk.single(_))))

  /**
   * Creates a single-valued stream from a managed resource
   */
  def managed[R, E, A](managed: ZManaged[R, E, A]): ZStream[R, E, A] =
    ZStream {
      for {
        doneRef   <- Ref.make(false).toManaged_
        finalizer <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull = ZIO.uninterruptibleMask { restore =>
          doneRef.get.flatMap { done =>
            if (done) ZIO.fail(None)
            else
              (for {

                reservation <- managed.reserve
                _           <- finalizer.add(reservation.release)
                a           <- restore(reservation.acquire)
              } yield Chunk(a)).mapError(Some(_))
          }
        }
      } yield pull
    }

  /**
   * The stream that never produces any value or fails with any error.
   */
  val never: ZStream[Any, Nothing, Nothing] =
    ZStream(ZManaged.succeedNow(UIO.never))

  /**
   * Like [[unfoldM]], but allows the emission of values to end one step further than
   * the unfolding of the state. This is useful for embedding paginated APIs,
   * hence the name.
   */
  def paginateM[R, E, A, S](s: S)(f: S => ZIO[R, E, (A, Option[S])]): ZStream[R, E, A] =
    ZStream {
      for {
        ref <- Ref.make[Option[S]](Some(s)).toManaged_
      } yield ref.get.flatMap {
        case Some(s) => f(s).foldM(e => ZIO.fail(Some(e)), { case (a, s) => ref.set(s).as(Chunk.single(a)) })
        case None    => ZIO.fail(None)
      }
    }

  /**
   * Creates a stream from an effect producing a value of type `A` which repeats forever
   */
  def repeatEffect[R, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] =
    fromEffect(fa).forever

  /**
   * Creates a stream from an effect producing values of type `A` until it fails with None.
   */
  def repeatEffectOption[R, E, A](fa: ZIO[R, Option[E], A]): ZStream[R, E, A] =
    ZStream(ZManaged.succeedNow(fa.map(Chunk.single(_))))

  /**
   * Creates a single-valued pure stream
   */
  def succeed[A](a: => A): ZStream[Any, Nothing, A] =
    fromChunk(Chunk.single(a))

  /**
   * A stream that contains a single `Unit` value.
   */
  val unit: ZStream[Any, Nothing, Unit] =
    ZStream.succeed(())

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  def unfoldM[R, E, A, S](s: S)(f0: S => ZIO[R, E, Option[(A, S)]]): ZStream[R, E, A] =
    unfoldChunkM(s)(f0(_).map(_.map {
      case (a, s) => Chunk.single(a) -> s
    }))

  /**
   * Creates a stream by effectfully peeling off the "layers" of a value of type `S`
   */
  def unfoldChunkM[R, E, A, S](s: S)(f0: S => ZIO[R, E, Option[(Chunk[A], S)]]): ZStream[R, E, A] =
    ZStream {
      for {
        done <- Ref.make(false).toManaged_
        ref  <- Ref.make(s).toManaged_
        pull = done.get.flatMap {
          if (_) ZIO.fail(None)
          else {
            ref.get
              .flatMap(f0)
              .foldM(
                e => ZIO.fail(Some(e)),
                opt =>
                  opt match {
                    case Some((a, s)) => ref.set(s).as(a)
                    case None         => done.set(true) *> ZIO.fail(None)
                  }
              )
          }
        }
      } yield pull
    }

  /**
   * Creates a stream produced from an effect
   */
  def unwrap[R, E, A](fa: ZIO[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    fromEffect(fa).flatten

  /**
   * Creates a stream produced from a [[ZManaged]]
   */
  def unwrapManaged[R, E, A](fa: ZManaged[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    managed(fa).flatten
}
