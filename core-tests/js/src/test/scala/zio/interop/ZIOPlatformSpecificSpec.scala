import zio._
import zio.test._
import zio.test.Assertion._

object ZIOPlatformSpecificSpec extends DefaultRunnableSpec {

  def spec: Spec[Environment, TestFailure[Throwable], TestSuccess] =
    suite("ZIOPlatformSpecific Tests")(
      testM("readFile should succeed with file content") {
        val fileContent = "Test file content"
        val fakeFile    = new js.File(Array(fileContent))

        for {
          result <- ZIOPlatformSpecific.readFile(fakeFile).either
        } yield assert(result)(isRight(equalTo(fileContent)))
      },
      testM("readFile should fail on error") {
        val fakeFile = new js.File(Array.empty)

        for {
          result <- ZIOPlatformSpecific.readFile(fakeFile).either
        } yield assert(result)(isLeft(anything))
      }
    )
}
