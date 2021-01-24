package zio
package system

import zio.test.Assertion._
import zio.test._
import zio.test.environment.{Live, live}

import scala.reflect.io.File

object SystemSpec extends ZIOBaseSpec {

  def spec: Spec[Live, TestFailure[Throwable], TestSuccess] = suite("SystemSpec")(
    suite("Fetch an environment variable and check that")(
      testM("If it exists, return a reasonable value") {
        assertM(live(system.env("PATH")))(isSome(containsString(File.separator + "bin")))
      },
      testM("If it does not exist, return None") {
        assertM(live(system.env("QWERTY")))(isNone)
      }
    ),
    suite("Fetch all environment variables and check that")(
      testM("If it exists, return a reasonable value") {
        assertM(live(system.envs.map(_.get("PATH"))))(isSome(containsString(File.separator + "bin")))
      },
      testM("If it does not exist, return None") {
        assertM(live(system.envs.map(_.get("QWERTY"))))(isNone)
      }
    ),
    suite("Fetch all VM properties and check that")(
      testM("If it exists, return a reasonable value") {
        assertM(live(properties.map(_.get("java.vm.name"))))(isSome(containsString("VM")))
      },
      testM("If it does not exist, return None") {
        assertM(live(properties.map(_.get("qwerty"))))(isNone)
      }
    ),
    suite("Fetch a VM property and check that")(
      testM("If it exists, return a reasonable value") {
        assertM(live(property("java.vm.name")))(isSome(containsString("VM")))
      },
      testM("If it does not exist, return None") {
        assertM(live(property("qwerty")))(isNone)
      }
    ),
    suite("Fetch the system's line separator and check that")(
      testM("it is identical to System.lineSeparator") {
        assertM(live(lineSeparator))(equalTo(java.lang.System.lineSeparator))
      }
    )
  )
}
