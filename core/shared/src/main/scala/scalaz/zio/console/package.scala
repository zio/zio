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

// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.io.{ IOException, PrintStream, Reader }

package object console extends Console.Interface[Console] {
  final val consoleService: ZIO[Console, Nothing, Console.Interface[Any]] =
    ZIO.access(_.console)

  /**
   * Prints text to the console.
   */
  final def putStr(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.accessM(_.console putStr line)

  final def putStr(stream: PrintStream)(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.accessM(_.console.putStr(stream)(line))

  /**
   * Prints a line of text to the console, including a newline character.
   */
  final def putStrLn(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.accessM(_.console putStrLn line)

  final def putStrLn(stream: PrintStream)(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.accessM(_.console.putStrLn(stream)(line))

  /**
   * Retrieves a line of input from the console.
   */
  final val getStrLn: ZIO[Console, IOException, String] =
    ZIO.accessM(_.console.getStrLn)

  final def getStrLn(reader: Reader): ZIO[Console, IOException, String] =
    ZIO.accessM(_.console getStrLn reader)

}
