/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

/**
 * The entry point for a purely-functional application on the JVM.
 *
 * {{{
 * import zio.App
 * import zio.console._
 *
 * object MyApp extends App {
 *
 *   final def run(args: List[String]) =
 *     myAppLogic.exitCode
 *
 *   def myAppLogic =
 *     for {
 *       _ <- putStrLn("Hello! What is your name?")
 *       n <- getStrLn
 *       _ <- putStrLn("Hello, " + n + ", good to meet you!")
 *     } yield ()
 * }
 * }}}
 */
trait App extends BootstrapRuntime {

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `IO` with the errors fully handled.
   */
  def run(args: List[String]): URIO[ZEnv, ExitCode]

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  // $COVERAGE-OFF$ Bootstrap to `Unit`
  final def main(args0: Array[String]): Unit =
    try sys.exit(
      unsafeRun(
        for {
          fiber <- run(args0.toList).fork
          _ <- IO.effectTotal(java.lang.Runtime.getRuntime.addShutdownHook(new Thread {
                override def run() = {
                  val _ = unsafeRunSync(fiber.interrupt)
                }
              }))
          result <- fiber.join
          _      <- fiber.interrupt
        } yield result.code
      )
    )
    catch { case _: SecurityException => }
  // $COVERAGE-ON$
}
