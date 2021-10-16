package zio.test.mock.module

import zio.stream.ZSink
import zio.test.mock.{Mock, Proxy}
import zio.{Has, UIO, URLayer, ZIO}

/**
 * Example module used for testing ZIO Mock framework.
 */
object StreamModuleMock extends Mock[StreamModule] {

  object Sink   extends Sink[Any, String, Int, Nothing, List[Int]]
  object Stream extends Stream[Any, String, Int]

  val compose: URLayer[Has[Proxy], StreamModule] =
    ZIO
      .service[Proxy]
      .flatMap { proxy =>
        withRuntime[Has[Proxy]].map { rts =>
          new StreamModule.Service {
            def sink(a: Int) =
              rts.unsafeRun(proxy(Sink, a).catchAll(error => UIO(ZSink.fail[String, Int](error).dropLeftover)))
            def stream(a: Int) = rts.unsafeRun(proxy(Stream, a))
          }
        }
      }
      .toLayer
}
