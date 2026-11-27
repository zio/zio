/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import scala.annotation.experimental
import scala.language.experimental.saferExceptions

/**
 * Experimental extensions that use experimental Scala 3 language features. The
 * object is marked `@experimental`, so it can only be used in an experimental
 * scope, for example inside a definition annotated with `@experimental`.
 */
@experimental
object Experimental {

  extension (zio: ZIO.type) {

    /**
     * Converts a value whose computation may throw exceptions into a `ZIO`
     * value. `a` is evaluated each time the effect runs: its value is the
     * success, and an exception that it throws is the failure. The exception is
     * not checked against `E` at run time, so any `java.lang.Exception` that
     * `a` throws fails the effect.
     */
    def fromThrows[E <: Exception, A](a: => A throws E): IO[E, A] =
      ZIO.suspendSucceed {
        try {
          Exit.succeed(a)
        } catch {
          case e: E => ZIO.fail(e)
        }
      }
  }
}
