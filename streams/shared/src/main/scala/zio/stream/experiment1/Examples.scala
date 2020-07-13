package zio
package stream.experiment1

object Examples extends App {

  val chunkStream = ZStream1(Chunk(1 to 100: _*))
  val stream      = ZStream1(1 to 100: _*)
  val transducer  = ZTransducer1.take[Nothing, Int](10)
  val sink        = ZSink1.sum[Nothing, Int]

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      s1 <- stream.run(sink)
      _  <- console.putStrLn(s"s1: $s1")
      s2 <- (stream :>> transducer).run(sink)
      _  <- console.putStrLn(s"s2: $s2")
      s3 <- stream.run(transducer >>: sink)
      _  <- console.putStrLn(s"s3: $s3")
      s4 <- chunkStream.run(sink.chunked)
      _  <- console.putStrLn(s"s4: $s4")
      s5 <- (chunkStream :>> transducer.chunked).run(sink.chunked)
      _  <- console.putStrLn(s"s5: $s5")
      s6 <- chunkStream.run(transducer.chunked >>: sink.chunked)
      _  <- console.putStrLn(s"s6: $s6")
    } yield ()).exitCode
}
