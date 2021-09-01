/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import zio.internal._

/**
 * The entry point for a ZIO application.
 *
 * {{{
 * import zio.ZIOApp
 * import zio.Console._
 *
 * object MyApp extends ZIOApp {
 *
 *   def run =
 *     for {
 *       _ <- printLine("Hello! What is your name?")
 *       n <- readLine
 *       _ <- printLine("Hello, " + n + ", good to meet you!")
 *     } yield ()
 * }
 * }}}
 */
trait ZIOApp { self =>
  @volatile private var shuttingDown = false

  /**
   * Composes this [[ZIOApp]] with another [[ZIOApp]], to yield an application that
   * executes the logic of both applications.
   */
  final def <>(that: ZIOApp): ZIOApp = ZIOApp(self.run.zipPar(that.run), self.hook >>> that.hook)

  /**
   * A helper function to obtain access to the command-line arguments of the
   * application. You may use this helper function inside your `run` function.
   */
  final def args: ZIO[Has[ZIOAppArgs], Nothing, Chunk[String]] = ZIO.service[ZIOAppArgs].map(_.args)

  /**
   * A helper function to exit the application with the specified exit code.
   */
  final def exit(code: ExitCode): UIO[Unit] =
    UIO {
      if (!shuttingDown) {
        shuttingDown = true
        try java.lang.System.exit(code.code)
        catch { case _: SecurityException => }
      }
    }

  /**
   * A hook into the ZIO platform used for boostrapping the application. This hook can
   * be used to install low-level functionality into the ZIO application, such as
   * logging, profiling, and other similar foundational pieces of infrastructure.
   */
  def hook: PlatformAspect = PlatformAspect.identity

  /**
   * Invokes the main app. Designed primarily for testing.
   */
  final def invoke(args: Chunk[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO.runtime[ZEnv].flatMap { runtime =>
      val newRuntime = runtime.mapPlatform(hook)

      newRuntime.run(run.provideCustomLayer(ZLayer.succeed(ZIOAppArgs(args)))).exitCode
    }

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    runtime.unsafeRun {
      (for {
        fiber <- invoke(Chunk.fromIterable(args0)).provide(runtime.environment).fork
        _ <-
          IO.succeed(Platform.addShutdownHook { () =>
            shuttingDown = true

            if (FiberContext.fatal.get) {
              println(
                "**** WARNING ****\n" +
                  "Catastrophic JVM error encountered. " +
                  "Application not safely interrupted. " +
                  "Resources may be leaked. " +
                  "Check the logs for more details and consider overriding `Platform.reportFatal` to capture context."
              )
            } else {
              try runtime.unsafeRunSync(fiber.interrupt)
              catch { case _: Throwable => }
            }

            ()
          })
        result <- fiber.join.tapErrorCause(ZIO.logCause(_)).exitCode
        _      <- exit(result)
      } yield ())
    }

    def runtime: Runtime[ZEnv] = Runtime.default
  }

  /**
   * The main function of the application, which can access the command-line arguments through
   * the `args` helper method of this class. If the provided effect fails for any reason, the
   * cause will be logged, and the exit code of the application will be non-zero. Otherwise,
   * the exit code of the application will be zero.
   */
  def run: ZIO[ZEnv with Has[ZIOAppArgs], Any, Any]
}
object ZIOApp {
  class Proxy(app: ZIOApp) extends ZIOApp {
    override final def hook = app.hook
    override final def run  = app.run
  }

  /**
   * Creates a [[ZIOApp]] from an effect, which can consume the arguments of the program, as well
   * as a hook into the ZIO runtime configuration.
   */
  def apply(run0: ZIO[ZEnv with Has[ZIOAppArgs], Any, Any], hook0: PlatformAspect): ZIOApp =
    new ZIOApp {
      override def hook = hook0
      def run           = run0
    }

  /**
   * Creates a [[ZIOApp]] from an effect, using the unmodified default runtime's platform.
   */
  def fromEffect(run0: ZIO[ZEnv with Has[ZIOAppArgs], Any, Any]): ZIOApp = ZIOApp(run0, PlatformAspect.identity)
}
