package scalaz.zio

package object stream {

  type Stream[+E, +A] = StreamR[Any, E, A]
  val Stream = StreamR

  type StreamPure[+A] = StreamPureR[Any, A]
  val StreamPure = StreamPureR

  type Sink[+E, +A0, -A, +B] = SinkR[Any, E, A0, A, B]
  val Sink = SinkR

}
