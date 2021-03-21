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

package zio.test.mock.module

import zio.stream.{Sink, Stream}
import zio.{URIO, ZIO}

/**
 * Example of ZIO Data Types module used for testing ZIO Mock framework.
 */
object StreamModule {

  trait Service {
    def sink(a: Int): Sink[String, Int, Nothing, List[Int]]
    def stream(a: Int): Stream[String, Int]
  }

  def sink(a: Int): URIO[StreamModule, Sink[String, Int, Nothing, List[Int]]] = ZIO.access[StreamModule](_.get.sink(a))
  def stream(a: Int): URIO[StreamModule, Stream[String, Int]]                 = ZIO.access[StreamModule](_.get.stream(a))
}
