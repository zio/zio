package zio.test

import scala.concurrent.ExecutionContext.Implicits.global

import zio.test.mock._
import zio.test.TestUtils.{ report, scope }

object TestMain {

  def main(args: Array[String]): Unit = {
    val testResults = List(
      scope(AssertResultSpec.run, "AssertResultSpec"),
      scope(ClockSpec.run, "ClockSpec"),
      scope(ConsoleSpec.run, "ConsoleSpec"),
      scope(DefaultTestReporterSpec.run, "DefaultTestReporterSpec"),
      scope(EnvironmentSpec.run, "EnvironmentSpec"),
      scope(GenSpec.run, "GenSpec"),
      scope(RandomSpec.run, "RandomSpec"),
      scope(SchedulerSpec.run, "SchedulerSpec"),
      scope(SystemSpec.run, "SystemSpec"),
      scope(PredicateSpec.run, "PredicateSpec")
    ).flatten
    report(testResults)
  }
}
