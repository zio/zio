package zio.stream

import zio.{LogAnnotation, Trace}
import zio.metrics.MetricLabel
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `ZStreamAspect` transforms a stream into a stream with the same
 * environment, error and element types. Apply one with `stream @@ aspect`. The
 * type parameters bound the environment (`LowerR`, `UpperR`), error (`LowerE`,
 * `UpperE`) and element (`LowerA`, `UpperA`) types of the streams that the
 * aspect accepts.
 *
 * The companion object has aspects that rechunk a stream (`rechunk`) and that
 * add log annotations (`annotated`) or metric tags (`tagged`) while it runs.
 * Compose aspects with `@@`, `>>>` or `andThen`.
 *
 * {{{
 * import zio._
 *
 * val stream: ZStream[Any, Nothing, Int] =
 *   ZStream.range(1, 8) @@ ZStreamAspect.rechunk(3)
 *
 * val chunkSizes: UIO[Chunk[Int]] = stream.chunks.map(_.size).runCollect
 * // chunkSizes succeeds with Chunk(3, 3, 1)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stream/zstream/ ZStream]]
 */
trait ZStreamAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] { self =>

  /**
   * Applies this aspect to the specified stream.
   */
  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](
    stream: ZStream[R, E, A]
  )(implicit trace: Trace): ZStream[R, E, A]

  /**
   * Returns a new aspect that applies this aspect to the stream first, then
   * `that` to the result. An alias for `andThen`.
   */
  def >>>[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    self.andThen(that)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one: it applies this aspect to the stream first,
   * then `that` to the result. An alias for `andThen`.
   */
  def @@[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    self >>> that

  /**
   * Returns a new aspect that applies this aspect to the stream first, then
   * `that` to the result. When both aspects add an effect in front of the
   * stream, the effect of `that` runs first, because `that` wraps the stream
   * that this aspect returns.
   */
  def andThen[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    new ZStreamAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] {
      def apply[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1, A >: LowerA1 <: UpperA1](
        stream: ZStream[R, E, A]
      )(implicit trace: Trace): ZStream[R, E, A] =
        that(self(stream))
    }
}

/**
 * Predefined aspects: `annotated`, `rechunk` and `tagged`. Each accepts streams
 * of any environment, error and element type.
 */
object ZStreamAspect {

  /**
   * An aspect that adds the specified log annotation while the stream runs.
   * Logs from the stream, and from operators applied after the aspect (for
   * example a later `mapZIO`), carry the annotation; it is removed when the
   * stream ends.
   */
  def annotated(key: String, value: String): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.logAnnotate(key, value) *> stream
    }

  /**
   * An aspect that adds the specified log annotations while the stream runs.
   * Logs from the stream, and from operators applied after the aspect (for
   * example a later `mapZIO`), carry the annotations; they are removed when the
   * stream ends.
   */
  def annotated(annotations: (String, String)*): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.logAnnotate(annotations.map { case (key, value) => LogAnnotation(key, value) }.toSet) *> stream
    }

  /**
   * An aspect that rechunks the stream into chunks of `n` elements. The last
   * chunk may hold fewer. When `n` is less than 1, the chunks hold one element
   * each.
   */
  def rechunk(n: Int): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        stream.rechunk(n)
    }

  /**
   * An aspect that adds the specified metric tag while the stream runs. Metrics
   * updated by the stream, and by operators applied after the aspect (for
   * example a later `mapZIO`), carry the tag; it is removed when the stream
   * ends.
   */
  def tagged(key: String, value: String): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.tagged(key, value) *> stream
    }

  /**
   * An aspect that adds the specified metric tags while the stream runs.
   * Metrics updated by the stream, and by operators applied after the aspect
   * (for example a later `mapZIO`), carry the tags; they are removed when the
   * stream ends.
   */
  def tagged(tags: (String, String)*): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.tagged(tags.map { case (key, value) => MetricLabel(key, value) }.toSet) *> stream
    }
}
