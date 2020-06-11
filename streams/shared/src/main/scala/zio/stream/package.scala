package zio

package object stream {
  type Stream[+E, +A] = ZStream[Any, E, A]
  val Stream = ZStream

  type Sink[+E, A, +L, +B] = ZSink[Any, E, A, L, B]
  val Sink = ZSink

  type Transducer[+E, -A, +B] = ZTransducer[Any, E, A, B]
  val Transducer = ZTransducer
}
