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

package zio.test.mock

import java.io.IOException

import zio.console.Console
import zio.{ Has, IO, UIO }
import zio.{ IO, UIO }

object MockConsole {

  object putStr   extends Method[Console.Service, String, Unit]
  object putStrLn extends Method[Console.Service, String, Unit]
  object getStrLn extends Method[Console.Service, Unit, String]

  implicit val mockableConsole: Mockable[Console.Service] = (mock: Mock) =>
    Has(new Console.Service {
      def putStr(line: String): UIO[Unit]   = mock(MockConsole.putStr, line)
      def putStrLn(line: String): UIO[Unit] = mock(MockConsole.putStrLn, line)
      val getStrLn: IO[IOException, String] = mock(MockConsole.getStrLn)
    })
}
