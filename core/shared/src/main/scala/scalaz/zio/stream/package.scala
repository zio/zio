package scalaz.zio

package object stream {

  type Stream[+E, +A] = StreamR[Any, E, A]

  type StreamPure[+A] = StreamPureR[Any, A]
  val StreamPure = StreamPureR

  type Sink[+E, +A0, -A, +B] = SinkR[Any, E, A0, A, B]
  val Sink = SinkR

  type StreamChunk[+E, +A] = StreamChunkR[Any, E, A]
  val StreamChunk = StreamChunkR

  type StreamChunkPure[+A] = StreamChunkPureR[Any, A]
  val streamChunkPure = StreamChunkPureR

}
