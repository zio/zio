package zio.stream.experimental

import zio.{UIO, ZLayer}

object zstreamTest_ServiceWith_01 extends App {

  trait SomeTrait {
    def live: UIO[Long]
  }

  println("zstreamTest_ServiceWith_01")

  // using examples from https://kazchimo.com/2021/06/07/zstream-companion-object-api/
  val longStream = ZStream
    .serviceWith[SomeTrait](_.live)
    .provideCustomLayer(ZLayer.succeed(new SomeTrait {
      override def live : UIO[Long] = {
        println("Updating Service")
        UIO(10L)
      }
    }))

  longStream.map(x => print(x))
}
