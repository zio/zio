package zio.stream

trait StreamAspect[-R, +E] {
  def apply[R1 <: R, E1 >: E, A](stream: ZStream[R1, E1, A]): ZStream[R1, E1, A]
}

object StreamAspect {

  /**
   * An aspect that rechunks the stream into chunks of the specified size.
   */
  def chunkN(n: Int): StreamAspect[Any, Nothing] =
    new StreamAspect[Any, Nothing] {
      def apply[R1, E1, A](stream: ZStream[R1, E1, A]): ZStream[R1, E1, A] =
        stream.chunkN(n)
    }
}
