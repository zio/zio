/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The entry point for a ZIO application.
 *
 * Extend it with an object and define `run`; the object gets a `main` method
 * that runs `run`. It is a [[ZIOApp]] whose `Environment` is `Any` and whose
 * `bootstrap` layer is empty, so `run` uses the default services (`Clock`,
 * `Console`, `Random`, `System`), the command-line arguments through
 * `getArgs`, and a `Scope`. When `run` succeeds, the exit code is 0; when it
 * fails or dies, the cause is logged and the exit code is 1.
 *
 * {{{
 * import zio.ZIOAppDefault
 * import zio.Console._
 *
 * object MyApp extends ZIOAppDefault {
 *
 *   def run =
 *     for {
 *       _ <- printLine("Hello! What is your name?")
 *       n <- readLine
 *       _ <- printLine("Hello, " + n + ", good to meet you!")
 *     } yield ()
 * }
 * }}}
 *
 * @see [[https://zio.dev/reference/core/zioapp/ ZIOApp]]
 */
trait ZIOAppDefault extends ZIOApp {

  /** `Any`: the app needs no services besides the default ones. */
  type Environment = Any

  /**
   * An empty layer that builds no services. Override it to change the runtime,
   * for example with `Runtime.setExecutor` or `Runtime.removeDefaultLoggers`.
   */
  val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = ZLayer.empty

  /** The tag of `Any`. */
  val environmentTag: EnvironmentTag[Any] = EnvironmentTag[Any]

}

/** Constructors that build a [[ZIOAppDefault]] from an effect. */
object ZIOAppDefault {

  /**
   * Creates a [[ZIOAppDefault]] whose `run` is the effect `run0`, which can
   * read the arguments of the program through `ZIOAppArgs`.
   */
  def apply(
    run0: ZIO[ZIOAppArgs, Any, Any]
  ): ZIOAppDefault =
    new ZIOAppDefault {
      def run = run0
    }

  /**
   * Creates a [[ZIOAppDefault]] from an effect, using the unmodified default
   * runtime's configuration.
   */
  def fromZIO(run0: ZIO[ZIOAppArgs, Any, Any]): ZIOAppDefault =
    ZIOAppDefault(run0)
}
