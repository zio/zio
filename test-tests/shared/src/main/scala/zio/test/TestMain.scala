package zio.test

import scala.concurrent.ExecutionContext.Implicits.global

import zio.test.mock.MockSpecSpec
import zio.test.environment._
import zio.test.TestUtils.{ report, scope }

object TestMain {

  def main(args: Array[String]): Unit = {
    val allTests: List[(String, ZIOBaseSpec)] = List(
      ("AssertionSpec", AssertionSpec),
      ("BoolAlgebraSpec", AssertionSpec),
      ("CheckSpec", CheckSpec),
      ("ClockSpec", ClockSpec),
      ("ConsoleSpec", ConsoleSpec),
      ("DefaultTestReporterSpec", DefaultTestReporterSpec),
      ("EnvironmentSpec", EnvironmentSpec),
      ("FunSpec", FunSpec),
      ("GenSpec", GenSpec),
      ("GenZIOSpec", GenZIOSpec),
      ("LiveSpec", LiveSpec),
      ("ManagedSpec", ManagedSpec),
      ("MockSpecSpec", MockSpecSpec),
      ("RandomSpec", RandomSpec),
      ("SampleSpec", SampleSpec),
      ("SchedulerSpec", SchedulerSpec),
      ("SystemSpec", SystemSpec),
      ("TestAspectSpec", TestAspectSpec),
      ("TestSpec", TestSpec)
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
