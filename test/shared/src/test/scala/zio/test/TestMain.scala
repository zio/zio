package zio.test

import scala.concurrent.ExecutionContext.Implicits.global

import zio.test.mock._
import zio.test.TestUtils.{ report, scope }

object TestMain {

  def main(args: Array[String]): Unit = {
    val testResults = List(
      scope(AssertionSpec.run, "Assertion"),
      scope(CheckSpec.run, "Check"),
      scope(ClockSpec.run, "MockClock"),
      scope(ConsoleSpec.run, "MockConsole"),
      scope(DefaultTestReporterSpec.run, "DefaultTestReporter"),
      scope(EnvironmentSpec.run, "MockEnvironment"),
      scope(GenSpec.run, "Gen"),
      scope(LiveSpec.run, "Live"),
      scope(RandomSpec.run, "MockRandom"),
      scope(SampleSpec.run, "Sample"),
      scope(SchedulerSpec.run, "MockScheduler"),
      scope(SystemSpec.run, "MockSystem")
    ).flatten
    report(testResults)
  }
}
