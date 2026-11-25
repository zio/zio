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

import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean

/**
 * An entry point for a ZIO application that allows sharing layers between
 * applications. For a simpler version that uses the default ZIO environment see
 * `ZIOAppDefault`.
 *
 * An object that extends `ZIOApp` gets a `main` method. It builds the services
 * of `bootstrap`, then runs `run` with them, the command-line arguments (as
 * `ZIOAppArgs`) and a `Scope`. When `run` succeeds, the exit code is 0; when
 * it fails or dies, the cause is logged and the exit code is 1.
 * `<>` composes two apps into one that runs both.
 *
 * {{{
 * trait Inventory { def count(sku: String): UIO[Int] }
 *
 * object InventoryApp extends ZIOApp {
 *   type Environment = Inventory
 *
 *   val environmentTag: EnvironmentTag[Inventory] = EnvironmentTag[Inventory]
 *
 *   val bootstrap: ZLayer[ZIOAppArgs, Any, Inventory] =
 *     ZLayer.succeed(new Inventory { def count(sku: String): UIO[Int] = ZIO.succeed(12) })
 *
 *   def run: ZIO[Inventory with ZIOAppArgs with Scope, Any, Any] =
 *     ZIO.serviceWithZIO[Inventory](_.count("SKU-1001")).flatMap(n => Console.printLine(s"In stock: \$n"))
 * }
 * }}}
 *
 * @see [[https://zio.dev/reference/core/zioapp/ ZIOApp]]
 */
trait ZIOApp extends ZIOAppPlatformSpecific with ZIOAppVersionSpecific {
  self =>
  private[zio] val shuttingDown = new AtomicBoolean(false)

  /** The tag of `Environment`, which ZIO uses to build the app's environment. */
  implicit def environmentTag: EnvironmentTag[Environment]

  /**
   * The services that `bootstrap` builds and that `run` can use, besides
   * `ZIOAppArgs` and `Scope`.
   */
  type Environment

  /**
   * A layer that manages the acquisition and release of services necessary for
   * the application to run.
   */
  def bootstrap: ZLayer[ZIOAppArgs, Any, Environment]

  /**
   * The main function of the application, which can access the command-line
   * arguments through the `getArgs` helper method of this class. If the
   * provided effect fails for any reason, the cause will be logged, and the
   * exit code of the application will be non-zero (1). Otherwise, the exit code
   * of the application will be zero, whatever value the effect succeeds with,
   * even an `ExitCode`; call `exit` to end with another code. An interruption
   * that comes from the app shutting down is not logged.
   */
  def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any]

  /**
   * The time that the application waits, after a shutdown signal such as
   * `SIGINT` interrupts `run`, for the finalizers to finish before the process
   * exits. The default, `Duration.Infinity`, waits until they finish, and a
   * zero or negative duration does not wait. When the time runs out, the app
   * prints a warning and the process exits.
   *
   * It is used on the JVM only: on Scala.js and Scala Native the app installs
   * no shutdown hook, so this value has no effect.
   */
  def gracefulShutdownTimeout: Duration = Duration.Infinity

  /**
   * The runtime that `main` runs the app on: `Runtime.default` unless
   * overridden. `bootstrap` can change its settings further.
   */
  def runtime: Runtime[Any] = Runtime.default

  /**
   * Composes this [[ZIOApp]] with another [[ZIOApp]], to yield an application
   * that executes the logic of both applications. The two `run` effects run in
   * parallel, so when one fails the other is interrupted, and the app's
   * `bootstrap` combines both `bootstrap` layers.
   */
  final def <>(that: ZIOApp)(implicit trace: Trace): ZIOApp = {
    def combine[A: EnvironmentTag, B: EnvironmentTag]: EnvironmentTag[A with B] = EnvironmentTag[A with B]

    ZIOApp(self.run.zipPar(that.run), self.bootstrap +!+ that.bootstrap)(
      combine[this.Environment, that.Environment](this.environmentTag, that.environmentTag)
    )
  }

  /**
   * A helper function to obtain access to the command-line arguments of the
   * application. You may use this helper function inside your `run` function.
   */
  final def getArgs(implicit trace: Trace): ZIO[ZIOAppArgs, Nothing, Chunk[String]] =
    ZIOAppArgs.getArgs

  /**
   * A helper function to exit the application with the specified exit code.
   *
   * On the JVM and Scala Native it calls `System.exit` at once, so the
   * finalizers of `run` do not run; it does nothing when the app is already
   * exiting, and on the JVM it does nothing inside an unforked sbt run. On
   * Scala.js it sets `process.exitCode` and does not stop the program.
   */
  final def exit(code: ExitCode)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(exitUnsafe(code)(Unsafe))

  protected[zio] def exitUnsafe(code: ExitCode)(implicit unsafe: Unsafe): Unit =
    if (shuttingDown.compareAndSet(false, true)) {
      try Platform.exit(code.code)
      catch {
        case _: SecurityException =>
      }
    }

  protected[zio] final def exitUnsafe(exit: Exit[Nothing, ExitCode])(implicit unsafe: Unsafe): Unit =
    exit match {
      case Exit.Success(code) => exitUnsafe(code)
      case _                  => exitUnsafe(ExitCode.failure)
    }

  protected[zio] final def workflow(args: Array[String])(implicit trace: Trace): ZIO[Any, Any, Any] =
    workflow(Chunk.fromIterable(args), false)

  protected[zio] final def workflow(args: Chunk[String], skipLogging: Boolean)(implicit
    trace: Trace
  ): ZIO[Any, Any, Any] = {
    val newLayer = ZLayer.succeed(ZIOAppArgs(args)) >>> bootstrap +!+ ZLayer.environment[ZIOAppArgs]

    (for {
      runtime <- ZIO.runtime[Environment with ZIOAppArgs]
      _       <- installSignalHandlers(runtime)
      result <- runtime.run(ZIO.scoped[Environment with ZIOAppArgs](run)).tapErrorCause { c =>
                  // Don't log an interruption error if we're shutting down
                  if (skipLogging || (shuttingDown.get() && c.isInterruptedOnly)) Exit.unit
                  else ZIO.logErrorCause(c)
                }
    } yield result)
      .provideLayer(newLayer.tapErrorCause(cause => if (skipLogging) Exit.unit else ZIO.logErrorCause(cause)))
  }

  /**
   * Invokes the main app. Designed primarily for testing.
   */
  final def invoke(args: Chunk[String])(implicit trace: Trace): ZIO[Any, Any, Any] =
    ZIO.suspendSucceed(workflow(args, true))

  protected def installSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] =
    ZIO.ignore {
      if (ZIOApp.installedSignals.compareAndSet(false, true)) {
        val dumpFibers =
          () => runtime.unsafe.run(Fiber.dumpAll)(trace, Unsafe.unsafe).getOrThrowFiberFailure()(Unsafe.unsafe)

        if (!System.os.isWindows) {
          Platform.addSignalHandler("INFO", dumpFibers)(Unsafe.unsafe)
          Platform.addSignalHandler("USR1", dumpFibers)(Unsafe.unsafe)
        }
      }
    }

  protected[zio] def interruptRootFibers(mainFiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    for {
      roots <- Fiber.roots
      _     <- Fiber.interruptAll(roots.view.filter(fiber => fiber.isAlive() && (fiber.id != mainFiberId)))
    } yield ()
}

/**
 * Constructors for [[ZIOApp]] values, and `Proxy`, which turns such a value
 * into a runnable object.
 */
object ZIOApp {
  private val installedSignals = new java.util.concurrent.atomic.AtomicBoolean(false)

  /**
   * A class which can be extended by an object to convert a description of a
   * ZIO application as a value into a runnable application.
   */
  class Proxy(val app: ZIOApp) extends ZIOApp {

    /** The environment of `app`. */
    type Environment = app.Environment

    /** Returns the `bootstrap` layer of `app`. */
    final def bootstrap: ZLayer[ZIOAppArgs, Any, Environment] =
      app.bootstrap

    override final def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
      app.run

    /** Returns the environment tag of `app`. */
    implicit final def environmentTag: EnvironmentTag[Environment] =
      app.environmentTag
  }

  /**
   * Creates a [[ZIOApp]] from an effect, which can consume the arguments of the
   * program, as well as a hook into the ZIO runtime configuration.
   */
  def apply[R](
    run0: ZIO[R with ZIOAppArgs with Scope, Any, Any],
    bootstrap0: ZLayer[ZIOAppArgs, Any, R]
  )(implicit tagged: EnvironmentTag[R]): ZIOApp =
    new ZIOApp {
      type Environment = R

      def environmentTag: EnvironmentTag[Environment] = tagged

      def bootstrap = bootstrap0

      def run = run0
    }

  /**
   * Creates a [[ZIOApp]] from an effect, using the unmodified default runtime's
   * configuration.
   */
  def fromZIO(run0: ZIO[ZIOAppArgs, Any, Any])(implicit trace: Trace): ZIOApp =
    ZIOApp(run0, ZLayer.environment)
}
