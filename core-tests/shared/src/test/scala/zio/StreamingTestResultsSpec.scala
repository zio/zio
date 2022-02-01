package zio.test.sbt
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import zio.stm._
import zio._

object StreamingTestResultsSpec extends ZIOSpecDefault{
    def spec = suite("StreamingTestResultsSpec")(
        test("StreamingTestResults should be able to be serialized") {
            for {
                state <- TRef.make[Chunk[TestCollectorState]](Chunk.empty).commit
                manager = new TestResultManager(state)
                _ <- ZIO.debug(state)
                _ <- ZIO.succeed(1)
            } yield assertTrue(true)
        }
    )
}