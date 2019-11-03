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

package zio

trait ManagedApp extends DefaultRuntime { ma =>

  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program.
   */
  def run(args: List[String]): ZManaged[Environment, Nothing, Unit]

  private val app = new App {
    override def run(args: List[String]): ZIO[Environment, Nothing, Unit] =
      ma.run(args).use(_ => ZIO.effectTotal(()))
  }

  final def main(args: Array[String]): Unit = app.main(args)
}
