package zio.test.mock

import scala.Predef.{ assert => SAssert, _ }

import zio.DefaultRuntime
import zio.test.mock.MockSystem.Data

object SystemSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(env1, "MockSystem fetch an environment variable and check that if it exists, return a reasonable value")
    SAssert(env2, "MockSystem fetch an environment variable and check that if it does not exist, return None")
    SAssert(env3, "MockSystem fetch an environment variable and check that if it is set, return the set value")
    SAssert(env4, "MockSystem fetch an environment variable and check that if it is cleared, return None")
    SAssert(prop1, "MockSystem fetch a VM property and check that if it exists, return a reasonable value")
    SAssert(prop2, "MockSystem fetch a VM property and check that if it does not exist, return None")
    SAssert(prop3, "MockSystem fetch a VM property and check that if it is set, return the set value")
    SAssert(prop4, "MockSystem fetch a VM property and check that if it is cleared, return None")
    SAssert(
      lineSep1,
      "MockSystem fetch the system's line separator and check that it is identical to Data.lineSeparator"
    )
    SAssert(lineSep2, "MockSystem fetch the system's line separator and check that if it is set, return the set value")
  }

  def env1 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data(envs = Map("k1" -> "v1")))
        env        <- mockSystem.env("k1")
      } yield env == Option("v1")
    )

  def env2 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        env        <- mockSystem.env("k1")
      } yield env == Option.empty
    )

  def env3 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        _          <- mockSystem.putEnv("k1", "v1")
        env        <- mockSystem.env("k1")
      } yield env == Option("v1")
    )

  def env4 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data(envs = Map("k1" -> "v1")))
        _          <- mockSystem.clearEnv("k1")
        env        <- mockSystem.env("k1")
      } yield env == None
    )

  def prop1 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data(properties = Map("k1" -> "v1")))
        prop       <- mockSystem.property("k1")
      } yield prop == Option("v1")
    )

  def prop2 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        prop       <- mockSystem.property("k1")
      } yield prop == Option.empty
    )

  def prop3 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        _          <- mockSystem.putProperty("k1", "v1")
        prop       <- mockSystem.property("k1")
      } yield prop == Option("v1")
    )

  def prop4 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data(properties = Map("k1" -> "v1")))
        _          <- mockSystem.clearProperty("k1")
        prop       <- mockSystem.property(("k1"))
      } yield prop == None
    )

  def lineSep1 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data(lineSeparator = ","))
        lineSep    <- mockSystem.lineSeparator
      } yield lineSep == ","
    )

  def lineSep2 =
    unsafeRun(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        _          <- mockSystem.setLineSeparator(",")
        lineSep    <- mockSystem.lineSeparator
      } yield lineSep == ","
    )
}
