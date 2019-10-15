package zio.test

import zio.test.Assertion.{anything, isCase, isSubtype}

import scala.reflect.ClassTag

//todo confirm this is ok - moved to package object because I couldn't figure out how to create a private def in suite scope
package object TestAspectSpecHelper {
  def failsWithException[E](implicit ct: ClassTag[E]): Assertion[TestFailure[E]] =
    isCase(
      "Runtime", {
        case TestFailure.Runtime(c) => c.dieOption
        case _                      => None
      },
      isSubtype[E](anything)
    )
}
