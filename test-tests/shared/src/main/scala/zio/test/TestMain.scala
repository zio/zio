package zio.test

import zio.test.TestUtils.{ report, scope }
import zio.test.environment._

import scala.concurrent.ExecutionContext.Implicits.global

object TestMain {

  def main(args: Array[String]): Unit = {
    val allTests: List[(String, AsyncBaseSpec)] = List(
      ("CheckSpec", CheckSpec),
      ("DefaultTestReporterSpec", DefaultTestReporterSpec),
      ("SummaryBuilderSpec", SummaryBuilderSpec),
      ("GenSpec", GenSpec),
      ("RandomSpec", RandomSpec),
      ("SampleSpec", SampleSpec)
    )

    val selectedTests = args match {
      case Array() =>
        allTests
      case Array(spec) =>
        val found = allTests.filter(_._1 == spec)
        if (found.isEmpty)
          sys.error("Unknown specfication: " ++ spec)

        found
      case _ =>
        sys.error("Only one or no arguments are supported")
    }

    val testResults = selectedTests.map { case (label, spec) => scope(spec.run, label) }

    report(testResults)
  }
}
