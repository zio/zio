package zio.test.environment

import zio.{Has, IO, ZIO, ZLayer, ZEnv}
import zio.console.Console
import zio.clock.Clock
import zio.random.Random
import zio.system.System

trait LivePlatformSpecific {

  /**
   * Constructs a new `Live` service that implements the `Live` interface.
   * This typically should not be necessary as `TestEnvironment` provides
   * access to live versions of all the standard ZIO environment types but
   * could be useful if you are mixing in interfaces to create your own
   * environment type.
   */
  def default: ZLayer[ZEnv, Nothing, Live] =
    ZLayer.fromFunction { (clock: Clock.Service, console: Console.Service, random: Random.Service, system: System.Service) =>
      Has(new Live.Service {
        val env = Has.allOf(clock, console, random, system)
        def provide[E, A](zio: ZIO[ZEnv, E, A]): IO[E, A] =
          zio.provide(env)
      })
    }


}
