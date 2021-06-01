package zio.stream.experimental

trait ZStreamAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] { self =>

  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](
    stream: ZStream[R, E, A]
  ): ZStream[R, E, A]

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
      ): ZStream[R, E, A] =
        that(self(stream))
    }
}

object ZStreamAspect {

  /**
   * An aspect that rechunks the stream into chunks of the specified size.
   */
  def chunkN(n: Int): ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A]): ZStream[R, E, A] =
        stream.chunkN(n)
    }
}
