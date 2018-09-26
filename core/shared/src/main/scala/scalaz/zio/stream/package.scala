package scalaz.zio

package object stream {
  type ChunkedSink[E, A, B] = Sink[E, A, Chunk[A], B]
}
