/*
 * Copyright 2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio._
import zio.test._

object FiberSetSpec extends ZIOSpecDefault {

  def spec = suite("FiberSetSpec")(
    test("add and iterate elements") {
      val set = new FiberSet()
      for {
        f1 <- ZIO.never.fork
        f2 <- ZIO.never.fork
        _ <- ZIO.succeed {
               set.add(f1.asInstanceOf[Fiber.Runtime[_, _]])
               set.add(f2.asInstanceOf[Fiber.Runtime[_, _]])
             }
        elements <- ZIO.succeed {
                      val bldr = ChunkBuilder.make[Fiber.Runtime[_, _]]()
                      val it   = set.iterator()
                      while (it.hasNext) bldr += it.next()
                      bldr.result()
                    }
      } yield assertTrue(elements.contains(f1) && elements.contains(f2) && elements.size == 2)
    },
    test("removes elements") {
      val set = new FiberSet()
      for {
        f1 <- ZIO.never.fork
        _  <- ZIO.succeed(set.add(f1.asInstanceOf[Fiber.Runtime[_, _]]))
        _  <- ZIO.succeed(set.remove(f1.asInstanceOf[Fiber.Runtime[_, _]]))
      } yield assertTrue(set.isEmpty)
    }
  )
}
