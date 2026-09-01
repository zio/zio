package zio.test

import java.util.concurrent.atomic.AtomicBoolean
import zio._

object ScopeAssertEvalSpec extends ZIOBaseSpec {

  private val aliveResource: ZIO[Scope, Nothing, AtomicBoolean] =
    ZIO.acquireRelease(
      ZIO.succeed(new AtomicBoolean(true))
    )(flag => ZIO.succeed(flag.set(false)))

  def spec = suite("ScopeAssertEvalSpec")(
    test("assertTrue captures inside ZIO.scoped see live resources") {
      ZIO.scoped {
        for {
          alive <- aliveResource
        } yield assertTrue(alive.get)
      }
    },
    test("eagerly captured Boolean inside ZIO.scoped works") {
      ZIO.scoped {
        for {
          alive  <- aliveResource
          isAlive = alive.get
        } yield assertTrue(isAlive)
      }
    },
    test("ZIO.succeed right before yield sees the flag set") {
      ZIO.scoped {
        for {
          alive   <- aliveResource
          isAlive <- ZIO.succeed(alive.get)
        } yield assertTrue(isAlive)
      }
    }
  )
}
