/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.mock

import zio.{IO, System, UIO, URLayer, ZIO, ZTraceElement}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

object MockSystem extends Mock[System] {

  object Env              extends Effect[String, SecurityException, Option[String]]
  object EnvOrElse        extends Effect[(String, String), SecurityException, String]
  object EnvOrOption      extends Effect[(String, Option[String]), SecurityException, Option[String]]
  object Envs             extends Effect[Unit, SecurityException, Map[String, String]]
  object Properties       extends Effect[Unit, Throwable, Map[String, String]]
  object Property         extends Effect[String, Throwable, Option[String]]
  object PropertyOrElse   extends Effect[(String, String), Throwable, String]
  object PropertyOrOption extends Effect[(String, Option[String]), Throwable, Option[String]]
  object LineSeparator    extends Effect[Unit, Nothing, String]

  val compose: URLayer[Proxy, System] = {
    implicit val trace = Tracer.newTrace
    ZIO
      .service[Proxy]
      .map(proxy =>
        new System {
          def env(variable: => String)(implicit trace: ZTraceElement): IO[SecurityException, Option[String]] =
            proxy(Env, variable)
          def envOrElse(variable: => String, alt: => String)(implicit
            trace: ZTraceElement
          ): IO[SecurityException, String] =
            proxy(EnvOrElse, variable, alt)
          def envOrOption(variable: => String, alt: => Option[String])(implicit
            trace: ZTraceElement
          ): IO[SecurityException, Option[String]] =
            proxy(EnvOrOption, variable, alt)
          def envs(implicit trace: ZTraceElement): IO[SecurityException, Map[String, String]] =
            proxy(Envs)
          def lineSeparator(implicit trace: ZTraceElement): UIO[String] =
            proxy(LineSeparator)
          def properties(implicit trace: ZTraceElement): IO[Throwable, Map[String, String]] =
            proxy(Properties)
          def property(prop: => String)(implicit trace: ZTraceElement): IO[Throwable, Option[String]] =
            proxy(Property, prop)
          def propertyOrElse(prop: => String, alt: => String)(implicit trace: ZTraceElement): IO[Throwable, String] =
            proxy(PropertyOrElse, prop, alt)
          def propertyOrOption(prop: => String, alt: => Option[String])(implicit
            trace: ZTraceElement
          ): IO[Throwable, Option[String]] =
            proxy(PropertyOrOption, prop, alt)

        }
      )
      .toLayer
  }
}
