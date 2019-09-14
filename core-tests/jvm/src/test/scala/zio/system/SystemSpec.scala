package zio
package system

import zio.test._
import zio.test.Assertion._

import scala.reflect.io.File

class SystemSpec
    extends ZIOBaseSpec(
      suite("SystemSpec")(
        suite("Fetch an environment variable and check that")(
          testM("If it exists, return a reasonable value") {
            assertM(system.env("PATH"), isSome(containsString(File.separator + "bin")))
          },
          testM("If it does not exist, return None") {
            assertM(system.env("QWERTY"), isNone)
          }
        ),
        suite("Fetch a VM property and check that")(
          testM("If it exists, return a reasonable value") {
            assertM(property("java.vm.name"), isSome(equalTo("VM")))
          },
          testM("If it does not exist, return None") {
            assertM(property("qwerty"), isNone)
          }
        ),
        suite("Fetch the system's line separator and check that")(
          testM("it is identical to System.lineSeparator") {
            assertM(lineSeparator, equalTo(java.lang.System.lineSeparator))
          }
        )
      )
    )
