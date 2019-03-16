package scalaz.zio

package object stream {

  type Stream[+E, +A] = ZStream[Any, E, A]

  type StreamChunk[+E, +A] = ZStreamChunk[Any, E, A]
  val StreamChunk = ZStreamChunk

  type Sink[+E, +A0, -A, +B] = ZSink[Any, E, A0, A, B]
  val Sink = ZSink

}
