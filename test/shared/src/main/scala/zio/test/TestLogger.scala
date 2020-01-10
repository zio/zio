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

import zio.console.Console
import zio.{ Has, UIO, URIO, ZIO, ZLayer }
import zio.{ UIO, URIO, ZIO }

object TestLogger {
  trait Service extends Serializable {
    def logLine(line: String): UIO[Unit]
  }

  def fromConsole: ZLayer[Console, Nothing, TestLogger] =
    ZLayer.fromService { (console: Console.Service) =>
      Has(new Service {
        def logLine(line: String): UIO[Unit] = console.putStrLn(line)
      })
    }

  def logLine(line: String): URIO[TestLogger, Unit] =
    ZIO.accessM(_.get.logLine(line))
}
