/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean

/**
 * An entry point for a ZIO application that allows sharing layers between
 * applications. For a simpler version that uses the default ZIO environment see
 * `ZIOAppDefault`.
 */
trait ZIOApp extends ZIOAppPlatformSpecific with ZIOAppVersionSpecific { self =>
  private[zio] val shuttingDown = new AtomicBoolean(false)

  implicit def environmentTag: EnvironmentTag[Environment]

  type Environment

  /**
   * A layer that manages the acquisition and release of services necessary for
   * the application to run.
   */
  def bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Environment]

  /**
   * The main function of the application, which can access the command-line
   * arguments through the `args` helper method of this class. If the provided
   * effect fails for any reason, the cause will be logged, and the exit code of
   * the application will be non-zero. Otherwise, the exit code of the
   * application will be zero.
   */
  def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any]

  /**
   * Composes this [[ZIOApp]] with another [[ZIOApp]], to yield an application
   * that executes the logic of both applications.
   */
  final def <>(that: ZIOApp)(implicit trace: Trace): ZIOApp =
    ZIOApp(self.run.zipPar(that.run), self.bootstrap +!+ that.bootstrap)

  /**
   * A helper function to obtain access to the command-line arguments of the
   * application. You may use this helper function inside your `run` function.
   */
  final def getArgs(implicit trace: Trace): ZIO[ZIOAppArgs, Nothing, Chunk[String]] =
    ZIOAppArgs.getArgs

  /**
   * A helper function to exit the application with the specified exit code.
   */
  final def exit(code: ExitCode)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      if (!shuttingDown.getAndSet(true)) {
        try Platform.exit(code.code)
        catch { case _: SecurityException => }
      }
    }

  /**
   * Invokes the main app. Designed primarily for testing.
   */
  final def invoke(args: Chunk[String])(implicit trace: Trace): ZIO[Any, Any, Any] =
    ZIO.suspendSucceed {
      val newLayer =
        Scope.default +!+ ZLayer.succeed(ZIOAppArgs(args)) >>>
          bootstrap +!+ ZLayer.environment[ZIOAppArgs with Scope]

      (for {
        runtime <- ZIO.runtime[Environment with ZIOAppArgs with Scope]
        _       <- installSignalHandlers(runtime)
        result  <- runtime.run(run)
      } yield result).provideLayer(newLayer)
    }

  def runtime: Runtime[Any] = Runtime.default

  protected def installSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] =
    ZIO.attempt {
      if (!ZIOApp.installedSignals.getAndSet(true)) {
        val dumpFibers =
          () => Unsafe.unsafeCompat(implicit u => runtime.unsafe.run(Fiber.dumpAll).getOrThrowFiberFailure)

        if (System.os.isWindows) {
          Platform.addSignalHandler("INT", dumpFibers)
        } else {
          Platform.addSignalHandler("INFO", dumpFibers)
          Platform.addSignalHandler("USR1", dumpFibers)
        }
      }
    }.ignore
}

object ZIOApp {
  private val installedSignals = new java.util.concurrent.atomic.AtomicBoolean(false)

  /**
   * A class which can be extended by an object to convert a description of a
   * ZIO application as a value into a runnable application.
   */
  class Proxy(val app: ZIOApp) extends ZIOApp {
    type Environment = app.Environment
    final def bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Environment] =
      app.bootstrap
    override final def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
      app.run
    implicit final def environmentTag: EnvironmentTag[Environment] =
      app.environmentTag
  }

  /**
   * Creates a [[ZIOApp]] from an effect, which can consume the arguments of the
   * program, as well as a hook into the ZIO runtime configuration.
   */
  def apply[R](
    run0: ZIO[R with ZIOAppArgs with Scope, Any, Any],
    bootstrap0: ZLayer[ZIOAppArgs with Scope, Any, R]
  )(implicit tagged: EnvironmentTag[R]): ZIOApp =
    new ZIOApp {
      type Environment = R
      def environmentTag: EnvironmentTag[Environment] = tagged
      def bootstrap                                   = bootstrap0
      def run                                         = run0
    }

  /**
   * Creates a [[ZIOApp]] from an effect, using the unmodified default runtime's
   * configuration.
   */
  def fromZIO(run0: ZIO[ZIOAppArgs, Any, Any])(implicit trace: Trace): ZIOApp =
    ZIOApp(run0, ZLayer.environment)
}
