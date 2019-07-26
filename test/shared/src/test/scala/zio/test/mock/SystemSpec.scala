package zio.test.mock

import zio.test.mock.TestSystem.Data
import zio.TestRuntime

class SystemSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "SystemSpec".title ^ s2"""
    Fetch an environment variable and check that:
      If it exists, return a reasonable value                         $env1
      If it does not exist, return None                               $env2
      It it is set, return the set value                              $env3

    Fetch a VM property and check that:
      If it exists, return a reasonable value                         $prop1
      If it does not exist, return None                               $prop2
      If it is set, return the set value                              $prop3

    Fetch the system's line separator and check that:
      It is identical to Data.lineSeparator                           $lineSep1
      If it is set, return the set value                              $lineSep2
  """

  def env1 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data(envs = Map("k1" -> "v1")))
        env        <- testSystem.env("k1")
      } yield env must_=== Option("v1")
    )

  def env2 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data())
        env        <- testSystem.env("k1")
      } yield env must_=== Option.empty
    )

  def env3 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data())
        _          <- testSystem.putEnv("k1", "v1")
        env        <- testSystem.env("k1")
      } yield env must_=== Option("v1")
    )

  def prop1 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data(properties = Map("k1" -> "v1")))
        prop       <- testSystem.property("k1")
      } yield prop must_=== Option("v1")
    )

  def prop2 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data())
        prop       <- testSystem.property("k1")
      } yield prop must_=== Option.empty
    )

  def prop3 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data())
        _          <- testSystem.putProperty("k1", "v1")
        prop       <- testSystem.property("k1")
      } yield prop must_=== Option("v1")
    )

  def lineSep1 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data(lineSeparator = ","))
        lineSep    <- testSystem.lineSeparator
      } yield lineSep must_=== ","
    )

  def lineSep2 =
    unsafeRun(
      for {
        testSystem <- TestSystem(Data())
        _          <- testSystem.setLineSeperator(",")
        lineSep    <- testSystem.lineSeparator
      } yield lineSep must_=== ","
    )
}
