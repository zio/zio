package zio.stream.experimental

import zio._
import zio.test.Assertion._
import zio.test._

object ZTransducerSpec extends ZIOBaseSpec {
  def spec = suite("ZTransducerSpec")(
    suite("Combinators")(),
    suite("Constructors")(
      testM("chunkN") {
        val parser = ZTransducer.chunkN[Int](5)
        val input  = List(Chunk(1), Chunk.empty, Chunk(2, 3, 4, 5), Chunk(6, 7), Chunk.empty, Chunk(8, 9, 10), Chunk(11))
        val result = parser.push.use(f =>
          ZIO
            .foldLeft(input)(List.empty[Chunk[Int]])((xs, x) => f(Some(x)).map(xs :+ _).catchAll(_ => ZIO.succeed(xs)))
            .flatMap(xs => f(None).map(xs :+ _))
        )
        assertM(result)(equalTo(List(Chunk(1, 2, 3, 4, 5), Chunk(6, 7, 8, 9, 10), Chunk(11))))
      },
      testM("collectAllWhile") {
        val parser = ZTransducer.collectAllWhile[Int](_ < 5)
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = parser.push.use(f =>
          ZIO
            .foldLeft(input)(List.empty[List[Int]])((xs, x) => f(Some(x)).map(_.fold(xs)(_ :+ _)).catchAll(_ => ZIO.succeed(xs)))
            .flatMap(xs => f(None).retry(Schedule.forever).map(_.fold(xs)(_ :+ _)))
        )
        assertM(result)(equalTo(List(List(3, 4), List(2, 3, 4), List(4, 3, 2))))
      }
    )
  )
}
