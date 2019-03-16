package scalaz.zio.testkit

import scalaz.zio.testkit.TestSystem.Data
import scalaz.zio.{ IO, Ref, TestRuntime }

class SystemSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "SystemSpec".title ^ s2"""
    Fetch an environment variable and check that:
      If it exists, return a reasonable value                         $env1
      If it does not exist, return None                               $env2

    Fetch a VM property and check that:
      If it exists, return a reasonable value                         $prop1
      If it does not exist, return None                               $prop2

    Fetch the system's line separator and check that:
      It is identical to Data.lineSeparator                           $lineSep1
  """

  def env1 =
    unsafeRun(
      for {
        data       <- Ref.make(Data(envs = Map("k1" -> "v1")))
        testSystem <- IO.succeed(TestSystem(data))
        env        <- testSystem.env("k1")
      } yield env must_=== Option("v1")
    )

  def env2 =
    unsafeRun(
      for {
        data       <- Ref.make(Data())
        testSystem <- IO.succeed(TestSystem(data))
        env        <- testSystem.env("k1")
      } yield env must_=== Option.empty
    )

  def prop1 =
    unsafeRun(
      for {
        data       <- Ref.make(Data(properties = Map("k1" -> "v1")))
        testSystem <- IO.succeed(TestSystem(data))
        prop       <- testSystem.property("k1")
      } yield prop must_=== Option("v1")
    )

  def prop2 =
    unsafeRun(
      for {
        data       <- Ref.make(Data())
        testSystem <- IO.succeed(TestSystem(data))
        prop       <- testSystem.property("k1")
      } yield prop must_=== Option.empty
    )

  def lineSep1 =
    unsafeRun(
      for {
        data       <- Ref.make(Data(lineSeparator = ","))
        testSystem <- IO.succeed(TestSystem(data))
        lineSep    <- testSystem.lineSeparator
      } yield lineSep must_=== ","
    )
}
