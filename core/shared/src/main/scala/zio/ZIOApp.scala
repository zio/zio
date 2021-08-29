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
abstract class ZIOApp { self =>

  /**
   * Composes this [[ZIOApp]] with another [[ZIOApp]], to yield an application that
   * executes the logic of both applications.
   */
  final def <>(that: ZIOApp): ZIOApp = ZIOApp(self.run.zipPar(that.run), self.hook >>> that.hook)

  /**
   * A helper function to obtain access to the command-line arguments of the
   * application. You may use this helper function inside your `run` function.
   *
   * {{{
   * import zio.ZIOApp
   * import zio.Console._
   *
   * object MyApp extends ZIOApp {
   *
   *   def run =
   *     for {
   *       a <- args
   *       _ <- putStrLn(s"The command-line arguments of the application are: ${a}")
   *     } yield ()
   * }
   * }}}
   */
  final def args: ZIO[Has[ZIOAppArgs], Nothing, Chunk[String]] = ZIO.service[ZIOAppArgs].map(_.args)

  /**
   * A helper function to exit the application with the specified exit code.
   */
  final def exit(code: ExitCode): UIO[Any] = UIO(java.lang.System.exit(code.code))

  /**
   * A hook into the ZIO platform used for boostrapping the application. This hook can
   * be used to install low-level functionality into the ZIO application, such as
   * logging, profiling, and other similar foundational pieces of infrastructure.
   */
  def hook: PlatformAspect = PlatformAspect.identity

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    val runtime = Runtime.default.mapPlatform(hook)

    runtime.unsafeRun {
      (for {
        fiber <- run.provide(runtime.environment ++ Has(ZIOAppArgs(Chunk.fromIterable(args0)))).fork
        _ <-
          IO.succeed(java.lang.Runtime.getRuntime.addShutdownHook(new Thread {
            override def run() =
              if (FiberContext.fatal.get) {
                println(
                  "**** WARNING ***\n" +
                    "Catastrophic JVM error encountered. " +
                    "Application not safely interrupted. " +
                    "Resources may be leaked. " +
                    "Check the logs for more details and consider overriding `Platform.reportFatal` to capture context."
                )
              } else {
                val _ = runtime.unsafeRunSync(fiber.interrupt)
              }
          }))
        result <- fiber.join.tapCause(cause => ZIO.logCause(cause)).exitCode
        _      <- fiber.interrupt
        _ <- UIO(
               try sys.exit(result.code)
               catch { case _: SecurityException => }
             )
      } yield ())
    }
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
    override def hook = app.hook
    def run           = app.run
  }

  def apply(run0: ZIO[ZEnv with Has[ZIOAppArgs], Any, Any], hook0: PlatformAspect): ZIOApp =
    new ZIOApp {
      override def hook = hook0
      def run           = run0
    }
}
