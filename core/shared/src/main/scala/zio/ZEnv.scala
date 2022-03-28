/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

object ZEnv {

  private[zio] object Services {
    val live: ZEnvironment[ZEnv] =
      ZEnvironment[Clock, Console, System, Random](
        Clock.ClockLive,
        Console.ConsoleLive,
        System.SystemLive,
        Random.RandomLive
      )
  }

  val any: ZLayer[ZEnv, Nothing, ZEnv] =
    ZLayer.environment[ZEnv](Tracer.newTrace)

  val live: Layer[Nothing, ZEnv] =
    Clock.live ++ Console.live ++ System.live ++ Random.live

  val clock: FiberRef[Clock] =
    FiberRef.unsafeMake(Clock.ClockLive)

  val console: FiberRef[Console] =
    FiberRef.unsafeMake(Console.ConsoleLive)

  val random: FiberRef[Random] =
    FiberRef.unsafeMake(Random.RandomLive)

  val system: FiberRef[System] =
    FiberRef.unsafeMake(System.SystemLive)

  def locally[R, E, A](clock: Clock, console: Console, random: Random, system: System)(
    zio: ZIO[R, E, A]
  )(implicit trace: ZTraceElement): ZIO[R, E, A] =
    ZIO.scoped[R] {
      for {
        _ <- ZEnv.clock.locallyScoped(clock)
        _ <- ZEnv.console.locallyScoped(console)
        _ <- ZEnv.random.locallyScoped(random)
        _ <- ZEnv.system.locallyScoped(system)
        a <- zio
      } yield a
    }
}
