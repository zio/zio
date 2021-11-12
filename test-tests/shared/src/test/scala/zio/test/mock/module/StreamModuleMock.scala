package zio.test.mock.module

import zio.stream.ZSink
import zio.test.mock.{Mock, Proxy}
import zio.{Has, UIO, URDeps, ZIO}

/**
 * Example module used for testing ZIO Mock framework.
 */
object StreamModuleMock extends Mock[StreamModule] {

  object Sink   extends Sink[Any, String, Int, String, Nothing, List[Int]]
  object Stream extends Stream[Any, String, Int]

  val compose: URDeps[Has[Proxy], StreamModule] =
    ZIO
      .service[Proxy]
      .flatMap { proxy =>
        withRuntime[Has[Proxy]].map { rts =>
          new StreamModule.Service {
            def sink(a: Int) =
              rts.unsafeRun(proxy(Sink, a).catchAll(error => UIO(ZSink.fail[String](error).dropLeftover)))
            def stream(a: Int) = rts.unsafeRun(proxy(Stream, a))
          }
        }
      }
      .toDeps
}
