// summary : ZIO learning - testing
// keywords : scala, zio, learning, pure-functional, @testable
// publish : gist
// authors : David Crosson
// license : Apache
// id : da808f22-e652-495f-85fe-756b14d6aca5
// created-on : 2021-04-02T16:22:22+02:00
// managed-by : https://github.com/dacr/code-examples-manager
// run-with : scala-cli $scriptFile

// ---------------------
//> using scala  "3.1.1"
//> using lib "dev.zio::zio:2.0.0-RC4"
//> using lib "dev.zio::zio-test:2.0.0-RC4"
// ---------------------

import zio.*
import zio.Duration._
import zio.test._
import zio.test.Assertion._

object Encapsulated { // Work around for some threading issue when used directly
  // -------------------------------------------------------------
  // The logic we want to test
  val alarmLogic = for {
    _        <- Console.printLine("how many seconds to wait for ?")
    duration <- Console.readLine.map(_.toInt)
    _ <- zio.ZIO.debug("Inputted duration: " + duration)
    _        <- Clock.sleep(duration.seconds)
    _        <- Console.printLine("Wake up !")
  } yield ()

  // -------------------------------------------------------------
  // the test logic - just provide custom environments :)
  val testLogic = suite("Alarm logic tests")(
    zio.test.test("alarm behavior") {
      for {
        _      <- TestConsole.feedLines("5")
        x      <- alarmLogic.fork
        _      <- TestClock.adjust(6.seconds)
        _ <- zio.ZIO.debug("pre-join")
        _      <- x.join
        _ <- zio.ZIO.debug("post-join")
        output <- TestConsole.output
        _ <- zio.ZIO.debug("Retrieved output")
      } yield assertTrue(output.contains("Wake up !\n"))
    }
  )
  // -------------------------------------------------------------
}

//object Example extends ZIOSpecDefault {
//  def spec = Encapsulated.testLogic
//}

object Example extends RunnableSpec[TestEnvironment, Any] {
  def spec = Encapsulated.testLogic

  override type Environment = TestEnvironment
  override type Failure     = Any

  override def aspects: List[TestAspectAtLeastR[Environment]] = List.empty

  override def runner: TestRunner[TestEnvironment, Any] = defaultTestRunner
}
