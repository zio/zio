/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

trait ManagedApp extends BootstrapRuntime { ma =>

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `ZManaged` with the errors fully handled.
   */
  def run(args: List[String]): ZManaged[ZEnv, Nothing, ExitCode]

  private val app = new App {
    override def run(args: List[String]): URIO[ZEnv, ExitCode] =
      ma.run(args).use(exit => ZIO.effectTotal(exit))
  }

  final def main(args: Array[String]): Unit = app.main(args)
}
