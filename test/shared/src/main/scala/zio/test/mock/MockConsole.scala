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

package zio.test.mock

import java.io.IOException

import zio.{ IO, UIO }
import zio.console.Console

trait MockConsole extends Console {

  val console: MockConsole.Service[Any]
}

object MockConsole {

  trait Service[R] extends Console.Service[R]

  object putStr   extends Method[MockConsole, String, Unit]
  object putStrLn extends Method[MockConsole, String, Unit]
  object getStrLn extends Method[MockConsole, Unit, String]

  implicit val mockable: Mockable[MockConsole] = (mock: Mock) =>
    new MockConsole {
      val console = new Service[Any] {
        def putStr(line: String): UIO[Unit]   = mock(MockConsole.putStr, line)
        def putStrLn(line: String): UIO[Unit] = mock(MockConsole.putStrLn, line)
        val getStrLn: IO[IOException, String] = mock(MockConsole.getStrLn)
      }
    }
}
