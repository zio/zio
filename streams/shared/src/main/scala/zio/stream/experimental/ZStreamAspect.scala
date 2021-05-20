package zio.stream.experimental

trait ZStreamAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] {
  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](
    stream: ZStream[R, E, A]
  ): ZStream[R, E, A]
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
