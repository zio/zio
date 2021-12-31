package zio.mock.module

import zio.stream.{Sink, Stream}
import zio.{URIO, ZIO}

/**
 * Example of ZIO Data Types module used for testing ZIO Mock framework.
 */
trait StreamModule {
  def sink(a: Int): Sink[String, Int, Nothing, List[Int]]
  def stream(a: Int): Stream[String, Int]
}

object StreamModule {
  def sink(a: Int): URIO[StreamModule, Sink[String, Int, Nothing, List[Int]]] =
    ZIO.service[StreamModule].map(_.sink(a))
  def stream(a: Int): URIO[StreamModule, Stream[String, Int]] =
    ZIO.service[StreamModule].map(_.stream(a))
}
