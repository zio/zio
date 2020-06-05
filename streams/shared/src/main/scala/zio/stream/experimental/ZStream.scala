package zio.stream.experimental

import zio._
import zio.stream.experimental.ZStream.Pull

/**
 * A `ZStream[R, E, O]` is a description of a program that, when evaluated,
 * may emit 0 or more values of type `O`, may fail with errors of type `E`
 * and uses an environment of type `R`. One way to think of `ZStream` is as a
 * `ZIO` program that could emit multiple values.
 *
 * Another analogue to `ZStream` is an imperative iterator:
 * {{{
 * trait Iterator[A] {
 *   def next: A
 * }
 * }}}
 *
 * This data type can emit multiple `A` values through multiple calls to `next`.
 * Similarly, embedded inside every `ZStream` is a ZIO program: `ZIO[R, Option[E], Chunk[O]]`.
 * This program will be repeatedly evaluated as part of the stream execution. For
 * every evaluation, it will emit a chunk of values or end with an optional failure.
 * A failure of type `None` signals the end of the stream.
 *
 * `ZStream` is a purely functional *pull* based stream. Pull based streams offer
 * inherent laziness and backpressure, relieving users of the need to manage buffers
 * between operators. As an optimization, `ZStream` does not emit single values, but
 * rather [[zio.Chunk]] values. This allows the cost of effect evaluation to be
 * amortized and most importantly, keeps primitives unboxed. This allows `ZStream`
 * to model network and file-based stream processing extremely efficiently.
 *
 * The last important attribute of `ZStream` is resource management: it makes
 * heavy use of [[ZManaged]] to manage resources that are acquired
 * and released during the stream's lifetime.
 *
 * `ZStream` forms a monad on its `O` type parameter, and has error management
 * facilities for its `E` type parameter, modeled similarly to [[ZIO]] (with some
 * adjustments for the multiple-valued nature of `ZStream`). These aspects allow
 * for rich and expressive composition of streams.
 *
 * The current encoding of `ZStream` is *not* safe for recursion. `ZStream` programs
 * that are defined in terms of themselves will leak memory. For example, the following
 * implementation of [[ZStream#forever]] is not heap-safe:
 * {{{
 * def forever = self ++ forever
 * }}}
 *
 * Instead, recursive operators must be defined explicitly. See the definition of
 * [[ZStream#forever]] for an example. This limitation will be lifted in the future.
 */
final class ZStream[-R, +E, +O] private (val pull: URManaged[R, Pull[R, E, O]]) {

  def >>>[R1 <: R, E1 >: E, I1 >: O, A](transducer: ZTransducer[R1, E1, I1, A]): ZStream[R1, E1, A] =
    aggregate(transducer)

  /**
   * Applies an aggregator to the stream, which converts one or more elements of type `A` into elements of type `B`.
   */
  def aggregate[R1 <: R, E1 >: E, O1 >: O, A](transducer: ZTransducer[R1, E1, O1, A]): ZStream[R1, E1, A] =
    ZStream(pull.zip(ZRef.makeManaged(false)).zipWith(transducer.process) {
      case ((pull, done), (step, last)) =>
        ZIO.ifM(done.get)(
          Pull.end,
          pull.foldCauseM(
            Pull.haltWith(last.foldCauseM(Pull.halt, _.fold[Pull[R1, E1, A]](Pull.end)(done.set(true).as(_)))),
            step(_).foldCauseM(Pull.halt, Pull.emit)
          )
        )
    })

  /**
   * Returns a stream made of the concatenation in strict order of all the streams
   * produced by passing each element of this stream to `f`.
   */
  def flatMap[R1 <: R, E1 >: E, A](f: O => ZStream[R1, E1, A]): ZStream[R1, E1, A] = {
    def go(
      outer: Pull[R1, E1, O],
      inner: Pull[R1, E1, A],
      finalizer: Ref[ZManaged.Finalizer]
    ): Pull[R1, E1, A] = {

      def next: ZIO[R1, Option[E1], Pull[R1, E1, A]] =
        finalizer.getAndSet(ZManaged.Finalizer.noop).flatMap(_.apply(Exit.unit)) *>
          outer.flatMap(i =>
            ZIO.uninterruptibleMask { restore =>
              for {
                releaseMap <- ZManaged.ReleaseMap.make
                pull       <- restore(f(i).pull.zio.provideSome[R1]((_, releaseMap)).map(_._2))
                _          <- finalizer.set(releaseMap.releaseAll(_, ExecutionStrategy.Sequential))
              } yield pull
            }
          )

      inner.catchAllCause(Cause.sequenceCauseOption(_).fold(next.flatMap(go(outer, _, finalizer)))(Pull.halt))
    }

    ZStream {
      for {
        outer     <- pull
        inner     <- ZManaged.succeedNow[Pull[R1, E1, A]](Pull.end)
        finalizer <- ZManaged.finalizerRef(ZManaged.Finalizer.noop)
      } yield go(outer, inner, finalizer)
    }
  }

  /**
   * Pulls and returns one element from this stream, without running the entire stream.
   *
   * @note This is, in some sense. equivalent to `Seq.head`.
   */
  def head: ZIO[R, Option[E], O] =
    pull.use(identity)

  /**
   * Transforms the elements of this stream using the supplied function.
   */
  def map[A](f: O => A): ZStream[R, E, A] =
    ZStream(pull.map(_.map(f)))

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, O1 >: O, Z](sink: ZSink[R1, E1, O1, Z]): ZIO[R1, E1, Z] =
    (pull.zip(ZRef.makeManaged(false)) <*> sink.process).use {
      case ((pull, exit), (push, done)) =>
        def go: ZIO[R1, E1, Z] =
          ZIO.ifM(exit.get)(
            done,
            pull.foldCauseM(
              Cause.sequenceCauseOption(_).fold(done)(ZIO.halt(_)),
              push(_).foldCauseM(Cause.sequenceCauseEither(_).fold(ZIO.halt(_), exit.set(true).as(_)), _ => go)
            )
          )
        go
    }

  /**
   * Runs the stream and collects all of its elements in to a chunk.
   */
  def runCollect: ZIO[R, E, Chunk[O]] =
    run(ZSink.collect[O])

  /**
   * A stream that emits values from this stream until the specified predicate evaluates to `true`.
   */
  def takeUntil(p: O => Boolean): ZStream[R, E, O] =
    ZStream(
      ZRef
        .makeManaged(false)
        .zipWith(pull)((done, outer) =>
          ZIO.ifM(done.get)(
            Pull.end,
            outer.foldCauseM(
              Pull.haltWith(Pull.end),
              i =>
                if (p(i)) done.set(true).as(i)
                else Pull.emit(i)
            )
          )
        )
    )

  /**
   * A stream that emits values from this stream while the specified predicate evaluates to `true`.
   */
  def takeWhile(p: O => Boolean): ZStream[R, E, O] =
    ZStream(
      ZRef
        .makeManaged(false)
        .zipWith(pull)((done, outer) =>
          ZIO.ifM(done.get)(
            Pull.end,
            outer.foldCauseM(
              Pull.haltWith(Pull.end),
              i =>
                if (p(i)) Pull.emit(i)
                else done.set(true) *> Pull.end
            )
          )
        )
    )
}

object ZStream {

  type Pull[-R, +E, +O] = ZIO[R, Option[E], O]

  /**
   * Creates a new [[ZStream]] from a managed effect that yields a value.
   * The effect will be evaluated repeatedly until it fails with a `None`
   * (to signify stream end) or a `Some(E)` (to signify stream failure).
   *
   * The stream evaluation guarantees proper acquisition and release of the [[ZManaged]].
   */
  def apply[R, E, O](pull: URManaged[R, Pull[R, E, O]]): ZStream[R, E, O] =
    new ZStream(pull)

  /**
   * Creates a pure stream from a variable list of values
   */
  def apply[O](i: O*): ZStream[Any, Nothing, O] =
    fromChunk(Chunk.fromIterable(i))

  /**
   * Creates a stream from a [[zio.Chunk]] of values
   */
  def fromChunk[O](chunk: Chunk[O]): ZStream[Any, Nothing, O] =
    ZStream(
      ZRef
        .makeManaged(chunk)
        .map(ref => ref.modify(rem => if (rem.isEmpty) (Pull.end, rem) else (Pull.emit(rem.head), rem.tail)).flatten)
    )

  /**
   * Creates a stream from an effect producing a value of type `O`
   */
  def fromEffect[R, E, O](z: ZIO[R, E, O]): ZStream[R, E, O] =
    apply(ZRef.makeManaged(false).map(done => ZIO.ifM(done.get)(Pull.end, Pull.fromEffect(z.ensuring(done.set(true))))))

  /**
   * Creates a stream from an effect producing a value of type `O` which repeats forever.
   */
  def repeatEffect[R, E, O](z: ZIO[R, E, O]): ZStream[R, E, O] =
    succeed(z.mapError(Some(_)))

  /**
   * Creates a stream from a pull.
   */
  def succeed[R, E, O](pull: Pull[R, E, O]): ZStream[R, E, O] =
    apply(ZManaged.succeedNow(pull))

  final object Pull {

    def emit[A](a: A): Pull[Any, Nothing, A] =
      ZIO.succeedNow(a)

    val end: Pull[Any, Nothing, Nothing] =
      ZIO.fail(None)

    def halt[E](cause: Cause[E]): Pull[Any, E, Nothing] =
      ZIO.halt(cause.map(Option.apply))

    def haltWith[R, E, A](pull: Pull[R, E, A]): Cause[Option[E]] => Pull[R, E, A] =
      Cause.sequenceCauseOption(_).fold(pull)(halt)

    def fromEffect[R, E, A](z: ZIO[R, E, A]): Pull[R, E, A] =
      z.mapError(Some(_))
  }
}
