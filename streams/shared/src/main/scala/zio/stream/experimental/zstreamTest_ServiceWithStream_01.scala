package zio.stream.experimental

import zio.ZLayer

object zstreamTest_ServiceWithStream_01 extends App {

  trait SomeTrait {
    def live: ZStream[Any, Nothing, Long]
  }

  println("zstreamTest_ServiceWithStream_01")

  // using examples from https://kazchimo.com/2021/06/07/zstream-companion-object-api/
  val longStream = ZStream
    .serviceWithStream[SomeTrait](_.live)
    .provideCustomLayer(ZLayer.succeed(new SomeTrait {
      override def live : ZStream[Any, Nothing, Long] = {
        println("Updating Service")
        ZStream(1L, 2L)
      }
    }))

  longStream.map(x => print(x))
}
