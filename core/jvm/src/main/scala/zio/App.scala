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

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The entry point for a purely-functional application on the JVM.
 *
 * {{{
 * import zio.App
 * import zio.Console._
 *
 * object MyApp extends App {
 *
 *   final def run(args: List[String]) =
 *     myAppLogic.exitCode
 *
 *   val myAppLogic =
 *     for {
 *       _ <- printLine("Hello! What is your name?")
 *       n <- readLine
 *       _ <- printLine("Hello, " + n + ", good to meet you!")
 *     } yield ()
 * }
 * }}}
 */
@deprecated("Use zio.ZIOAppDefault", "2.0.0")
trait App extends ZApp[Any] with BootstrapRuntime
