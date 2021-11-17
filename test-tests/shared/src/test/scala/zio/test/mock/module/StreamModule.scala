package zio.test.mock.module

import zio.stream.{Sink, Stream}
import zio.{URIO, ZIO}

/**
 * Example of ZIO Data Types module used for testing ZIO Mock framework.
 */
object StreamModule {

  trait Service {
    def sink(a: Int): Sink[String, Int, String, Nothing, List[Int]]
    def stream(a: Int): Stream[String, Int]
  }

  def sink(a: Int): URIO[StreamModule.Service, Sink[String, Int, String, Nothing, List[Int]]] =
    ZIO.service[StreamModule.Service].map(_.sink(a))
  def stream(a: Int): URIO[StreamModule.Service, Stream[String, Int]] =
    ZIO.service[StreamModule.Service].map(_.stream(a))
}
