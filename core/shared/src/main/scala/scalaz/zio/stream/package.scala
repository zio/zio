package scalaz.zio

package object stream {

  type Stream[+E, +A] = StreamR[Any, E, A]
  val Stream = StreamR

  type StreamPure[+A] = StreamRPure[Any, A]
  val StreamPure = StreamRPure

}
