package zio.test.mock.module

import zio.stream.{Sink, Stream}
import zio.{URIO, ZIO}

/**
 * Example of ZIO Data Types module used for testing ZIO Mock framework.
 */
object StreamModule {

  trait Service {
    def sink(a: Int): Sink[String, Int, Nothing, List[Int]]
    def stream(a: Int): Stream[String, Int]
  }

  def sink(a: Int): URIO[StreamModule, Sink[String, Int, Nothing, List[Int]]] = ZIO.access[StreamModule](_.get.sink(a))
  def stream(a: Int): URIO[StreamModule, Stream[String, Int]]                 = ZIO.access[StreamModule](_.get.stream(a))
}
