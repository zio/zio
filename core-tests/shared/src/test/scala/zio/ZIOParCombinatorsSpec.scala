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
package zio

import zio.test._
import zio.test.Assertion._

object ZIOParCombinatorsSpec
    extends ZIOBaseSpec(
      suite("ZIO *Par combinators")(
        testM("foreachPar_ happy-path") {
          val as = Seq(1, 2, 3, 4, 5)
          for {
            ref <- Ref.make(Seq.empty[Int])
            _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
            rs  <- ref.get
          } yield assert(rs.sorted, equalTo(as))
        },
        testM("foreachPar_ huge") {
          val as = (1 to 1600).toList
          for {
            ref <- Ref.make(List.empty[Int])
            _   <- ZIO.foreachPar_(as)(a => ref.update(_ :+ a))
            rs  <- ref.get
          } yield assert(rs.sorted, equalTo(as))
        },
        testM("foreachPar_ cancels other computations and propagates errors") {
          val boom = new Exception("Boom!")
          for {
            semaphore <- Semaphore.make(0)
            error <- ZIO
                      .foreachPar_(Seq(1, 2, 3, 4, 5)) { a =>
                        if (a == 5) ZIO.fail(boom)
                        else (ZIO.never).onInterrupt(semaphore.release)
                      }
                      .flip
            _ <- semaphore.acquireN(4)
          } yield assert(error, equalTo(boom))
        },
        testM("foreachPar_ cancels other computations and propagates defects") {
          val boom = new Exception("Boom!")
          for {
            semaphore <- Semaphore.make(0)
            error <- ZIO
                      .foreachPar_(Seq(1, 2, 3, 4, 5)) { a =>
                        if (a == 5) ZIO.effectTotal(throw boom)
                        else ZIO.never.onInterrupt(semaphore.release)
                      }
                      .sandbox
                      .either
            _ <- semaphore.acquireN(4)
          } yield assert(error, isLeft(anything))
        },
        testM("testForeachParN_ uses up to N threads") {
          val as = Seq(1, 2, 3, 4, 5)
          for {
            ref     <- Ref.make(Seq.empty[Int])
            counter <- Ref.make(0)
            _ <- ZIO.foreachParN_(2)(as) { a =>
                  for {
                    c <- counter.update(_ + 1)
                    _ <- if (c > 2) ZIO.fail("too many threads!") else ZIO.succeed(())
                    _ <- ref.update(_ :+ a)
                    _ <- counter.update(_ - 1)
                  } yield ()
                }
            rs <- ref.get
          } yield assert(rs.sorted, equalTo(as))
        }
      )
    )
