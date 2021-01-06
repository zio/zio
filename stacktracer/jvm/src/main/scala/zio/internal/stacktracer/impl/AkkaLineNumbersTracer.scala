/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio.internal.stacktracer.impl

import zio.internal.stacktracer.ZTraceElement.{NoLocation, SourceLocation}
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer.lambdaNamePattern
import zio.internal.stacktracer.{Tracer, ZTraceElement}

import scala.util.matching.Regex

/**
 * A [[Tracer]] implementation powered by Akka's `LineNumbers` bytecode parser (shipped with ZIO, no dependency on Akka)
 */
final class AkkaLineNumbersTracer extends Tracer {
  final def traceLocation(lambda: AnyRef): ZTraceElement =
    AkkaLineNumbers(lambda) match {
      case AkkaLineNumbers.NoSourceInfo =>
        NoLocation(s"couldn't find class file for lambda:$lambda")

      case AkkaLineNumbers.UnknownSourceFormat(explanation) =>
        NoLocation(s"couldn't parse class file for lambda:$lambda, error: $explanation")

      case AkkaLineNumbers.SourceFile(filename) =>
        SourceLocation(filename, "<unknown>", "<unknown>", 0)

      case AkkaLineNumbers.SourceFileLines(filename, from, _, classNameSlashes, methodAnonfun) =>
        val className = classNameSlashes.replace('/', '.')
        val methodName = lambdaNamePattern
          .findFirstMatchIn(methodAnonfun)
          .flatMap(Option apply _.group(1))
          .getOrElse(methodAnonfun)

        SourceLocation(filename.intern(), className.intern(), methodName.intern(), from)
    }

}

private object AkkaLineNumbersTracer {
  private final val lambdaNamePattern: Regex = """\$anonfun\$(.+?)\$\d""".r
}
