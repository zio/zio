package zio.stream.experimental

import zio.ZIOBaseSpec
import zio.stream.experimental.ZStreamGen._
import zio.test.Assertion.{ equalTo, isTrue, succeeds }
import zio.test._

object ZSinkSpec extends ZIOBaseSpec {
  def spec = suite("ZSinkSpec")(
    suite("Constructors")(
      testM("collectAllToSet")(
        assertM(
          ZStream(1, 2, 3, 3, 4)
            .run(ZSink.collectAllToSet[Int])
        )(equalTo(Set(1, 2, 3, 4)))
      ),
      testM("collectAllToMap")(
        assertM(
          ZStream
            .range(0, 10)
            .run(ZSink.collectAllToMap[Int, Int](value => value % 3)(_ + _))
        )(equalTo(Map[Int, Int](0 -> 18, 1 -> 12, 2 -> 15)))
      ),
      testM("head")(
        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
          val headOpt = ZStream.fromChunks(chunks: _*).run(ZSink.head[Int])
          assertM(headOpt)(equalTo(chunks.flatMap(_.toSeq).headOption))
        }
      ),
      testM("last")(
        checkM(Gen.listOf(Gen.small(Gen.chunkOfN(_)(Gen.anyInt)))) { chunks =>
          val lastOpt = ZStream.fromChunks(chunks: _*).run(ZSink.last[Int])
          assertM(lastOpt)(equalTo(chunks.flatMap(_.toSeq).lastOption))
        }
      ),
      testM("foldLeft")(
        checkM(
          Gen.small(pureStreamGen(Gen.anyInt, _)),
          Gen.function2(Gen.anyString),
          Gen.anyString
        ) { (s, f, z) =>
          for {
            xs <- s.run(ZSink.foldLeft(z)(f))
            ys <- s.runCollect.map(_.foldLeft(z)(f))
          } yield assert(xs)(equalTo(ys))
        }
      ),
      suite("fold")(
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= 5)((a, b) => a + b)))(equalTo(6))
        ),
        testM("immediate termination")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= -1)((a, b) => a + b)))(equalTo(0))
        ),
        testM("termination in the middle")(
          assertM(ZStream.range(1, 10).run(ZSink.fold[Int, Int](0)(_ <= 500)((a, b) => a + b)))(equalTo(45))
        )
      ),
      suite("foldM")(
        testM("foldM") {
          val ioGen = successes(Gen.anyString)
          checkM(Gen.small(pureStreamGen(Gen.anyInt, _)), Gen.function2(ioGen), ioGen) { (s, f, z) =>
            for {
              sinkResult <- z.flatMap(z => s.run(ZSink.foldLeftM(z)(f)))
              foldResult <- s.fold(List[Int]())((acc, el) => el :: acc)
                             .map(_.reverse)
                             .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
                             .run
            } yield assert(foldResult.succeeded)(isTrue) implies assert(foldResult)(succeeds(equalTo(sinkResult)))
          }
        }
      )
    ),
    suite("Combinators")()
  )
}
