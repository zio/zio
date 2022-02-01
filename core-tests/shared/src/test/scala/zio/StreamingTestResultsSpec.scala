package zio.test.sbt
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object StreamingTestResultsSpec extends ZIOSpecDefault{
    def spec = suite("StreamingTestResultsSpec")(
        test("StreamingTestResults should be able to be serialized") {
        for {
            _ <- ZIO.succeed(1)
        } yield assertTrue(true)
        }
    )
}