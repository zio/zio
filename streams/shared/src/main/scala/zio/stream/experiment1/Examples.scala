package zio
package stream.experiment1

import zio.console.Console

object Examples extends App {

  val chunkStream = ZStream1(Chunk(1 to 100: _*))
  val stream      = ZStream1(1 to 100: _*)
  val transducer  = ZTransducer1.take[Int](10)
  val sink        = ZSink1.sum[Int]
  val pipe        = stream >>: ZTransducer1.service[Console.Service, Int]((c, i) => c.getStrLn.asSomeError.map(_.take(i)))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      s1 <- stream >>: sink
      _  <- console.putStrLn(s"s1: $s1")
      s2 <- stream >>: transducer >>: sink
      _  <- console.putStrLn(s"s2: $s2")
      s3 <- chunkStream >>: sink.chunked
      _  <- console.putStrLn(s"s4: $s3")
      s4 <- chunkStream >>: transducer.chunked >>: sink.chunked
      _  <- console.putStrLn(s"s5: $s4")
    } yield ()).exitCode
}
