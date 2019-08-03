package zio.test.mock

import scala.concurrent.{ ExecutionContext, Future }

import zio.DefaultRuntime
import zio.test.mock.MockSystem.Data
import zio.test.TestUtils.label

object SystemSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(env1, "MockSystem fetch an environment variable and check that if it exists, return a reasonable value"),
    label(env2, "MockSystem fetch an environment variable and check that if it does not exist, return None"),
    label(env3, "MockSystem fetch an environment variable and check that if it is set, return the set value"),
    label(env4, "MockSystem fetch an environment variable and check that if it is cleared, return None"),
    label(prop1, "MockSystem fetch a VM property and check that if it exists, return a reasonable value"),
    label(prop2, "MockSystem fetch a VM property and check that if it does not exist, return None"),
    label(prop3, "MockSystem fetch a VM property and check that if it is set, return the set value"),
    label(prop4, "MockSystem fetch a VM property and check that if it is cleared, return None"),
    label(
      lineSep1,
      "MockSystem fetch the system's line separator and check that it is identical to Data.lineSeparator"
    ),
    label(lineSep2, "MockSystem fetch the system's line separator and check that if it is set, return the set value")
  )

  def env1 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data(envs = Map("k1" -> "v1")))
        env        <- mockSystem.env("k1")
      } yield env == Option("v1")
    )

  def env2 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        env        <- mockSystem.env("k1")
      } yield env == Option.empty
    )

  def env3 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        _          <- mockSystem.putEnv("k1", "v1")
        env        <- mockSystem.env("k1")
      } yield env == Option("v1")
    )

  def env4 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data(envs = Map("k1" -> "v1")))
        _          <- mockSystem.clearEnv("k1")
        env        <- mockSystem.env("k1")
      } yield env == None
    )

  def prop1 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data(properties = Map("k1" -> "v1")))
        prop       <- mockSystem.property("k1")
      } yield prop == Option("v1")
    )

  def prop2 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        prop       <- mockSystem.property("k1")
      } yield prop == Option.empty
    )

  def prop3 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        _          <- mockSystem.putProperty("k1", "v1")
        prop       <- mockSystem.property("k1")
      } yield prop == Option("v1")
    )

  def prop4 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data(properties = Map("k1" -> "v1")))
        _          <- mockSystem.clearProperty("k1")
        prop       <- mockSystem.property(("k1"))
      } yield prop == None
    )

  def lineSep1 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data(lineSeparator = ","))
        lineSep    <- mockSystem.lineSeparator
      } yield lineSep == ","
    )

  def lineSep2 =
    unsafeRunToFuture(
      for {
        mockSystem <- MockSystem.makeMock(Data())
        _          <- mockSystem.setLineSeparator(",")
        lineSep    <- mockSystem.lineSeparator
      } yield lineSep == ","
    )
}
