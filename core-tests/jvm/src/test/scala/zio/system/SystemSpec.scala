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
            for {
              io <- system.env("PATH")
            } yield assert(io, isSome(hasSubstring(File.separator + "bin")))
          },
          testM("Fetch an environment variable and check that If it does not exist, return None") {
            for {
              io <- system.env("QWERTY")
            } yield assert(io, isNone)
          }
        ),
        suite("Fetch a VM property and check that")(
          testM("If it exists, return a reasonable value") {
            for {
              io <- property("java.vm.name")
            } yield assert(io, isSome(equalTo("VM")))
          },
          testM("If it does not exist, return None") {
            for {
              io <- property("qwerty")
            } yield assert(io, isNone)
          }
        ),
        suite("Fetch the system's line separator and check that")(
          testM("it is identical to System.lineSeparator") {
            for {
              separator <- lineSeparator
            } yield assert(separator, equalTo(java.lang.System.lineSeparator))
          }
        )
      )
    )
