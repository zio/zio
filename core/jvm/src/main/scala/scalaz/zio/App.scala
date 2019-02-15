/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.duration.Duration

/**
 * The entry point for a purely-functional application on the JVM.
 *
 * {{{
 * import java.io.IOException
 * import scalaz.zio.{App, IO}
 * import scalaz.zio.console._
 *
 * object MyApp extends App {
 *
 *   final def run(args: List[String]): UIO[ExitStatus] =
 *     myAppLogic.provide(Console.Live).attempt.map(_.fold(_ => 1, _ => 0)).map(ExitStatus.ExitNow(_))
 *
 *   def myAppLogic: ZIO[Console, IOException, Unit] =
 *     for {
 *       _ <- putStrLn("Hello! What is your name?")
 *       n <- getStrLn
 *       _ <- putStrLn("Hello, " + n + ", good to meet you!")
 *     } yield ()
 * }
 * }}}
 */
trait App extends RTS {
  sealed abstract class ExitStatus extends Serializable with Product
  object ExitStatus extends Serializable {
    case class ExitNow(code: Int)                         extends ExitStatus
    case class ExitWhenDone(code: Int, timeout: Duration) extends ExitStatus
    case object DoNotExit                                 extends ExitStatus
  }

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `IO` with the errors fully handled.
   */
  def run(args: List[String]): UIO[ExitStatus]

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  // $COVERAGE-OFF$ Bootstrap to `Unit`
  final def main(args0: Array[String]): Unit =
    unsafeRun(
      for {
        fiber <- run(args0.toList).fork
        _ <- IO.sync(Runtime.getRuntime.addShutdownHook(new Thread {
              override def run() = {
                val _ = unsafeRunSync(fiber.interrupt)
              }
            }))
        result <- fiber.join
      } yield result
    ) match {
      case ExitStatus.ExitNow(code) =>
        sys.exit(code)
      case ExitStatus.ExitWhenDone(code, _) =>
        // TODO: Shutdown
        sys.exit(code)
      case ExitStatus.DoNotExit =>
    }
  // $COVERAGE-ON$
}
