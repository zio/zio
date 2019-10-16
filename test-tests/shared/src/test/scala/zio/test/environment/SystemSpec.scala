package zio.test.environment

import zio.test.Assertion._
import zio.test.environment.TestSystem.Data
import zio.test.{ ZIOBaseSpec, suite, _ }

object SystemSpec
    extends ZIOBaseSpec(
      suite("SystemSpec")(
        testM("fetch an environment variable and check that if it exists, return a reasonable value") {
          for {
            testSystem <- TestSystem.makeTest(Data(envs = Map("k1" -> "v1")))
            env        <- testSystem.env("k1")
          } yield assert(env, equalTo(Option("v1")))
        },
        testM("fetch an environment variable and check that if it does not exist, return None") {
          for {
            testSystem <- TestSystem.makeTest(Data())
            env        <- testSystem.env("k1")
          } yield assert(env, equalTo(Option.empty))
        },
        testM("fetch an environment variable and check that if it is set, return the set value") {
          for {
            testSystem <- TestSystem.makeTest(Data())
            _          <- testSystem.putEnv("k1", "v1")
            env        <- testSystem.env("k1")
          } yield assert(env, equalTo(Option("v1")))
        },
        testM("fetch an environment variable and check that if it is cleared, return None") {
          for {
            testSystem <- TestSystem.makeTest(Data(envs = Map("k1" -> "v1")))
            _          <- testSystem.clearEnv("k1")
            env        <- testSystem.env("k1")
          } yield assert(env, equalTo(None))
        },
        testM("fetch a VM property and check that if it exists, return a reasonable value") {
          for {
            testSystem <- TestSystem.makeTest(Data(properties = Map("k1" -> "v1")))
            prop       <- testSystem.property("k1")
          } yield assert(prop, equalTo(Option("v1")))
        },
        testM("fetch a VM property and check that if it does not exist, return None") {
          for {
            testSystem <- TestSystem.makeTest(Data())
            prop       <- testSystem.property("k1")
          } yield assert(prop, equalTo(Option.empty))
        },
        testM("fetch a VM property and check that if it is set, return the set value") {
          for {
            testSystem <- TestSystem.makeTest(Data())
            _          <- testSystem.putProperty("k1", "v1")
            prop       <- testSystem.property("k1")
          } yield assert(prop, equalTo(Option("v1")))
        },
        testM("fetch a VM property and check that if it is cleared, return None") {
          for {
            testSystem <- TestSystem.makeTest(Data(properties = Map("k1" -> "v1")))
            _          <- testSystem.clearProperty("k1")
            prop       <- testSystem.property(("k1"))
          } yield assert(prop, equalTo(None))
        },
        testM("fetch the system's line separator and check that it is identical to Data.lineSeparator") {
          for {
            testSystem <- TestSystem.makeTest(Data(lineSeparator = ","))
            lineSep    <- testSystem.lineSeparator
          } yield assert(lineSep, equalTo(","))
        },
        testM("fetch the system's line separator and check that if it is set, return the set value") {
          for {
            testSystem <- TestSystem.makeTest(Data())
            _          <- testSystem.setLineSeparator(",")
            lineSep    <- testSystem.lineSeparator
          } yield assert(lineSep, equalTo(","))
        }
      )
    )
