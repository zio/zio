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

import scala.language.implicitConversions

trait AppOps {
  self: BootstrapRuntime =>

  implicit def mkAppOps(program: ZIO[ZEnv, Any, Any]): ZIOMain = new ZIOMain(program)

  class ZIOMain(program: ZIO[ZEnv, Any, Any]) {
    def createValueForBusiness(): ZIO[Any, Nothing, Int] = program.provide(environment).foldCause(e => {
      System.err.println(e.prettyPrint)
      1
    }, _ => 0)
  }
}