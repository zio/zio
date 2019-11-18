package zio
package system

import zio.test._
import zio.test.environment.live
import zio.test.Assertion._

import scala.reflect.io.File

object SystemSpec extends ZIOBaseSpec {

  def spec = suite("SystemSpec")(
    suite("Fetch an environment variable and check that")(
      testM("If it exists, return a reasonable value") {
        assertM(live(system.env("PATH")), isSome(containsString(File.separator + "bin")))
      },
      testM("If it does not exist, return None") {
        assertM(live(system.env("QWERTY")), isNone)
      }
    ),
    suite("Fetch a VM property and check that")(
      testM("If it exists, return a reasonable value") {
        assertM(live(property("java.vm.name")), isSome(containsString("VM")))
      },
      testM("If it does not exist, return None") {
        assertM(live(property("qwerty")), isNone)
      }
    ),
    suite("Fetch the system's line separator and check that")(
      testM("it is identical to System.lineSeparator") {
        assertM(live(lineSeparator), equalTo(java.lang.System.lineSeparator))
      }
    )
  )
}
