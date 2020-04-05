package zio.stream.experimental

import zio._
import zio.test.Assertion._
import zio.test._

object ZTransducerSpec extends ZIOBaseSpec {
  def run[R, E, I, O](parser: ZTransducer[R, E, I, O], input: List[Chunk[I]]): ZIO[R, E, List[Chunk[O]]] =
    parser.push.use { f =>
      def go(os0: List[Chunk[O]], i: Chunk[I]): ZIO[R, E, List[Chunk[O]]] =
        f(Some(i)).foldM(
          _.fold[ZIO[R, E, List[Chunk[O]]]](IO.succeed(os0))(IO.fail(_)),
          os => IO.succeed(if (os.isEmpty) os0 else os :: os0)
        )

      def finish(os0: List[Chunk[O]]): ZIO[R, E, List[Chunk[O]]] =
        f(None)
          .repeat(Schedule.doWhile(_.isEmpty))
          .map(_ :: os0)
          .catchAll(_.fold[ZIO[R, E, List[Chunk[O]]]](IO.succeed(os0))(IO.fail(_)))

      ZIO.foldLeft(input)(List.empty[Chunk[O]])(go).flatMap(finish).map(_.reverse)
    }

  def spec = suite("ZTransducerSpec")(
    suite("Combinators")(),
    suite("Constructors")(
      testM("chunkN") {
        val parser = ZTransducer.chunkN[Int](5)
        val input  = List(Chunk(1), Chunk.empty, Chunk(2, 3, 4, 5), Chunk(6, 7), Chunk.empty, Chunk(8, 9, 10), Chunk(11))
        val result = run(parser, input)
        assertM(result)(equalTo(List(Chunk(1, 2, 3, 4, 5), Chunk(6, 7, 8, 9, 10), Chunk(11))))
      },
      testM("collectAllWhile") {
        val parser = ZTransducer.collectAllWhile[Int](_ < 5)
        val input  = List(Chunk(3, 4, 5, 6, 7, 2), Chunk.empty, Chunk(3, 4, 5, 6, 5, 4, 3, 2), Chunk.empty)
        val result = run(parser, input)
        assertM(result)(equalTo(List(Chunk(List(3, 4)), Chunk(List(2, 3, 4)), Chunk(List(4, 3, 2)))))
      }
    )
  )
}
