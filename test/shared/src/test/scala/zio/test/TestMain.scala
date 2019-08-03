package zio.test

import scala.concurrent.ExecutionContext.Implicits.global

import zio.test.mock._
import zio.test.TestUtils.report

object TestMain {

  def main(args: Array[String]): Unit = {
    val testResults = List(
      ClockSpec.run,
      ConsoleSpec.run,
      DefaultTestReporterSpec.run,
      EnvironmentSpec.run,
      RandomSpec.run,
      SchedulerSpec.run,
      SystemSpec.run,
      PredicateSpec.run
    ).flatten
    report(testResults)
  }
}
