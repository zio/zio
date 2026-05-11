package zio.test

import zio.{Trace, ZIO}

import scala.language.implicitConversions

trait TestResultVersionSpecific {
  implicit def liftTestResultToZIOImplicit[R, E](result: TestResult)(implicit trace: Trace): ZIO[R, E, TestResult] =
    TestResult.liftTestResultToZIO(result)
}
