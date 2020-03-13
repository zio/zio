/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.UIO

import scala.compiletime.testing.typeChecks

trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. Dies with a runtime
   * exception if specified string cannot be parsed or is not a known value at
   * compile time.
   */
  inline final def typeCheck(inline code: String): UIO[Either[String, Unit]] =
    try {
      if (typeChecks(code)) UIO.succeedNow(Right(()))
      else UIO.succeedNow(Left(errorMessage))
    } catch {
      case _: Throwable => UIO.die(new RuntimeException("Compilation failed"))
    }

  private val errorMessage =
    "Reporting of compilation error messages on Dotty is not currently supported due to instability of the underlying APIs."
}
