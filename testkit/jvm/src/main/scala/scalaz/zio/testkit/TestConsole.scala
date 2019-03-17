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

package scalaz.zio.testkit

import java.io.IOException

import scalaz.zio.console._
import scalaz.zio._
import TestConsole.Data

case class TestConsole(ref: Ref[TestConsole.Data]) extends Console.Service[Any] {
  override def putStr(line: String): UIO[Unit] =
    ref.update { data =>
      Data(data.input, data.output :+ line)
    }.void

  override def putStrLn(line: String): ZIO[Any, Nothing, Unit] =
    ref.update { data =>
      Data(data.input, data.output :+ s"$line\n")
    }.void

  val getStrLn: ZIO[Any, IOException, String] = {
    for {
      input <- ref.get.flatMap(
                d =>
                  IO.fromOption(d.input.headOption)
                    .mapError(_ => new IOException("There is no more input left to read"))
              )
      _ <- ref.update { data =>
            Data(data.input.tail, data.output)
          }
    } yield input
  }
}

object TestConsole {
  case class Data(input: List[String] = List.empty, output: Vector[String] = Vector.empty)
}
