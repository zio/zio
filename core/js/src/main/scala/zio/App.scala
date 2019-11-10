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

trait App extends DefaultRuntime {

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int]

  /**
   * The Scala main function, intended to be called only by the Scala runtime.
   */
  final def main(args0: Array[String]): Unit = {
    if (!args0.isEmpty) {
      import zio.zmx._
      val config = args0.sliding(2, 1).toList.foldLeft(ZMXConfig.empty) { case (accumArgs, currArgs) => currArgs match {
        case Array("-h", host) => accumArgs.copy(host = host)
        case Array("--host", host) => accumArgs.copy(host = host)
        case Array("-p", port) => accumArgs.copy(port = port.toInt)
        case Array("--port", port) => accumArgs.copy(port = port.toInt)
        case Array("-d", debug) => accumArgs.copy(debug = debug.toBoolean)
        case Array("--debug", debug) => accumArgs.copy(debug = debug.toBoolean)
        case _ => accumArgs
        }
      }
      println(s"Starting zmx JS server on host: [${config.host}] port: [${config.port}]")
      ZMXServer(config)
    }
    unsafeRunAsync(run(args0.toList))(_ => ())
  }
}
