package zio

package object stream {

  type Stream[+E, +A] = ZStream[Any, E, A]

  type StreamChunk[+E, +A] = ZStreamChunk[Any, E, A]
  final val StreamChunk = ZStreamChunk

  type Sink[+E, +A0, -A, +B] = ZSink[Any, E, A0, A, B]

}
