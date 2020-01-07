package zio.test

import scala.concurrent.ExecutionContext.Implicits.global

import zio.test.TestUtils.{ report, scope }

object TestMain {

  def main(args: Array[String]): Unit = {
    val allTests: List[(String, AsyncBaseSpec)] = List(
      ("SampleSpec", SampleSpec)
    )

    val selectedTests = args match {
      case Array() =>
        allTests
      case Array(spec) =>
        val found = allTests.filter(_._1 == spec)
        if (found.isEmpty)
          sys.error("Unknown specification: " ++ spec)

        found
      case _ =>
        sys.error("Only one or no arguments are supported")
    }

    val testResults = selectedTests.map { case (label, spec) => scope(spec.run, label) }

    report(testResults)
  }
}
