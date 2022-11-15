package zio.stream

import zio.{LogAnnotation, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait ZStreamAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] { self =>

  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](
    stream: ZStream[R, E, A]
  )(implicit trace: Trace): ZStream[R, E, A]

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
   * aspect with the specified one.
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

object ZStreamAspect {

  /**
   * An aspect that annotates each log in this stream with the specified log
   * annotation.
   */
  def annotated(key: String, value: String): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.logAnnotate(key, value) *> stream
    }

  /**
   * An aspect that annotates each log in this stream with the specified log
   * annotations.
   */
  def annotated(annotations: (String, String)*): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.logAnnotate(annotations.map { case (key, value) => LogAnnotation(key, value) }.toSet) *> stream
    }

  /**
   * An aspect that rechunks the stream into chunks of the specified size.
   */
  def rechunk(n: Int): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        stream.rechunk(n)
    }

  /**
   * An aspect that tags each metric in this stream with the specified tag.
   */
  def tagged(key: String, value: String): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.tagged(key, value) *> stream
    }
}
