package zio.test.environment

import zio.test.Async
import zio.test.environment.TestSystem.Data
import zio.test.TestUtils.label
import zio.test.AsyncBaseSpec

object SystemSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(env1, "fetch an environment variable and check that if it exists, return a reasonable value"),
    label(env2, "fetch an environment variable and check that if it does not exist, return None"),
    label(env3, "fetch an environment variable and check that if it is set, return the set value"),
    label(env4, "fetch an environment variable and check that if it is cleared, return None"),
    label(prop1, "fetch a VM property and check that if it exists, return a reasonable value"),
    label(prop2, "fetch a VM property and check that if it does not exist, return None"),
    label(prop3, "fetch a VM property and check that if it is set, return the set value"),
    label(prop4, "fetch a VM property and check that if it is cleared, return None"),
    label(lineSep1, "fetch the system's line separator and check that it is identical to Data.lineSeparator"),
    label(lineSep2, "fetch the system's line separator and check that if it is set, return the set value")
  )

  def env1 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data(envs = Map("k1" -> "v1")))
        env        <- testSystem.env("k1")
      } yield env == Option("v1")
    )

  def env2 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data())
        env        <- testSystem.env("k1")
      } yield env == Option.empty
    )

  def env3 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data())
        _          <- testSystem.putEnv("k1", "v1")
        env        <- testSystem.env("k1")
      } yield env == Option("v1")
    )

  def env4 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data(envs = Map("k1" -> "v1")))
        _          <- testSystem.clearEnv("k1")
        env        <- testSystem.env("k1")
      } yield env == None
    )

  def prop1 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data(properties = Map("k1" -> "v1")))
        prop       <- testSystem.property("k1")
      } yield prop == Option("v1")
    )

  def prop2 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data())
        prop       <- testSystem.property("k1")
      } yield prop == Option.empty
    )

  def prop3 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data())
        _          <- testSystem.putProperty("k1", "v1")
        prop       <- testSystem.property("k1")
      } yield prop == Option("v1")
    )

  def prop4 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data(properties = Map("k1" -> "v1")))
        _          <- testSystem.clearProperty("k1")
        prop       <- testSystem.property(("k1"))
      } yield prop == None
    )

  def lineSep1 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data(lineSeparator = ","))
        lineSep    <- testSystem.lineSeparator
      } yield lineSep == ","
    )

  def lineSep2 =
    unsafeRunToFuture(
      for {
        testSystem <- TestSystem.makeTest(Data())
        _          <- testSystem.setLineSeparator(",")
        lineSep    <- testSystem.lineSeparator
      } yield lineSep == ","
    )
}
