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

import zio.Chunk
import zio.stream.ZStream
import zio.test.mock.module.{ StreamModule, StreamModuleMock }
import zio.test.{ suite, Assertion, TestAspect, ZIOBaseSpec }

object PolyStreamMockSpec extends ZIOBaseSpec with MockSpecUtils[StreamModule] {

  import Assertion._
  import Expectation._
  import TestAspect.exceptDotty

  private def successfulStream[E, A](elements: A*): ZStream[Any, E, A] = ZStream.fromIterable(elements)
  private def failedStream[E, A](error: E): ZStream[Any, E, A]         = ZStream.fail(error)

  def spec = suite("PolyStreamMockSpec")(
    suite("polymorphic input")(
      suite("expectations met")(
        testValue("String")(
          StreamModuleMock.PolyInputStream
            .of[String](equalTo("foo"), value(successfulStream[String, String]("bar", "baz"))),
          StreamModule.polyInputStream("foo").flatMap(_.runCollect),
          equalTo(Chunk("bar", "baz"))
        ),
        testValue("Int")(
          StreamModuleMock.PolyInputStream.of[Int](equalTo(42), value(successfulStream[String, String]("bar", "baz"))),
          StreamModule.polyInputStream(42).flatMap(_.runCollect),
          equalTo(Chunk("bar", "baz"))
        )
      )
    ),
    suite("polymorphic error")(
      suite("expectations met")(
        testError("String")(
          StreamModuleMock.PolyErrorStream.of[String](equalTo("foo"), value(failedStream[String, String]("bar"))),
          StreamModule.polyErrorStream("foo").flatMap(_.runCollect),
          equalTo("bar")
        ),
        testError("Int")(
          StreamModuleMock.PolyErrorStream.of[Int](equalTo("foo"), value(failedStream[Int, String](42))),
          StreamModule.polyErrorStream("foo").flatMap(_.runCollect),
          equalTo(42)
        )
      )
    ),
    suite("polymorphic output")(
      suite("expectations met")(
        testValue("String")(
          StreamModuleMock.PolyOutputStream
            .of[String](equalTo("foo"), value(successfulStream[String, String]("bar", "baz"))),
          StreamModule.polyOutputStream("foo").flatMap(_.runCollect),
          equalTo(Chunk("bar", "baz"))
        ),
        testValue("Int")(
          StreamModuleMock.PolyOutputStream.of[Int](equalTo("foo"), value(successfulStream[String, Int](42, 7))),
          StreamModule.polyOutputStream("foo").flatMap(_.runCollect),
          equalTo(Chunk(42, 7))
        )
      )
    ),
    suite("polymorphic input and error")(
      suite("expectations met")(
        suite("Long, Int")(
          testValue("success")(
            StreamModuleMock.PolyInputErrorStream
              .of[Long, Int](equalTo(42L), value(successfulStream[Int, String]("foo", "bar"))),
            StreamModule.polyInputErrorStream(42L).flatMap(_.runCollect),
            equalTo(Chunk("foo", "bar"))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputErrorStream.of[Long, Int](equalTo(42L), value(failedStream[Int, String](42))),
            StreamModule.polyInputErrorStream(42L).flatMap(_.runCollect),
            equalTo(42)
          )
        ),
        suite("String, Long")(
          testValue("success")(
            StreamModuleMock.PolyInputErrorStream
              .of[String, Long](equalTo("foo"), value(successfulStream[Long, String]("bar", "baz"))),
            StreamModule.polyInputErrorStream("foo").flatMap(_.runCollect),
            equalTo(Chunk("bar", "baz"))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputErrorStream
              .of[String, Long](equalTo("foo"), value(failedStream[Long, String](42L))),
            StreamModule.polyInputErrorStream("foo").flatMap(_.runCollect),
            equalTo(42L)
          )
        ),
        testValue("combined")(
          StreamModuleMock.PolyInputErrorStream
            .of[Long, Int](equalTo(42L), value(successfulStream[Int, String]("foo", "bar"))) andThen
            StreamModuleMock.PolyInputErrorStream
              .of[Int, Long](equalTo(42), value(successfulStream[Long, String]("baz", "qux"))),
          for {
            v1     <- StreamModule.polyInputErrorStream[Long, Int](42L)
            chunk1 <- v1.runCollect
            v2     <- StreamModule.polyInputErrorStream[Int, Long](42)
            chunk2 <- v2.runCollect
          } yield chunk1 ++ chunk2,
          equalTo(Chunk("foo", "bar", "baz", "qux"))
        )
      )
    ),
    suite("polymorphic input and output")(
      suite("expectations met")(
        suite("Long, Int")(
          testValue("success")(
            StreamModuleMock.PolyInputOutputStream
              .of[Long, Int](equalTo(42L), value(successfulStream[String, Int](5, 7))),
            StreamModule.polyInputOutputStream(42L).flatMap(_.runCollect),
            equalTo(Chunk(5, 7))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputOutputStream.of[Long, Int](equalTo(42L), value(failedStream[String, Int]("foo"))),
            StreamModule.polyInputOutputStream(42L).flatMap(_.runCollect),
            equalTo("foo")
          )
        ),
        suite("Int, Long")(
          testValue("success")(
            StreamModuleMock.PolyInputOutputStream
              .of[Int, Long](equalTo(42), value(successfulStream[String, Long](5, 7))),
            StreamModule.polyInputOutputStream(42).flatMap(_.runCollect),
            equalTo(Chunk(5, 7))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputOutputStream.of[Int, Long](equalTo(42), value(failedStream[String, Long]("foo"))),
            StreamModule.polyInputOutputStream(42).flatMap(_.runCollect),
            equalTo("foo")
          )
        ),
        testValue("combined")(
          StreamModuleMock.PolyInputOutputStream
            .of[Long, Int](equalTo(42L), value(successfulStream[String, Int](1, 2))) andThen
            StreamModuleMock.PolyInputOutputStream
              .of[Int, Long](equalTo(42), value(successfulStream[String, Long](3L, 4L))),
          for {
            v1     <- StreamModule.polyInputOutputStream[Long, Int](42L)
            chunk1 <- v1.runCollect
            v2     <- StreamModule.polyInputOutputStream[Int, Long](42)
            chunk2 <- v2.runCollect
          } yield (chunk1, chunk2),
          equalTo((Chunk(1, 2), Chunk(3L, 4L)))
        )
      )
    ),
    suite("polymorphic error and output")(
      suite("expectations met")(
        suite("Long, Int")(
          testValue("success")(
            StreamModuleMock.PolyErrorOutputStream
              .of[Long, Int](equalTo("foo"), value(successfulStream[Long, Int](1, 2))),
            StreamModule.polyErrorOutputStream("foo").flatMap(_.runCollect),
            equalTo(Chunk(1, 2))
          ),
          testError("failure")(
            StreamModuleMock.PolyErrorOutputStream.of[Long, Int](equalTo("foo"), value(failedStream[Long, Int](42L))),
            StreamModule.polyErrorOutputStream("foo").flatMap(_.runCollect),
            equalTo(42L)
          )
        ),
        suite("Int, Long")(
          testValue("success")(
            StreamModuleMock.PolyErrorOutputStream
              .of[Int, Long](equalTo("foo"), value(successfulStream[Int, Long](1L, 2L))),
            StreamModule.polyErrorOutputStream("foo").flatMap(_.runCollect),
            equalTo(Chunk(1L, 2L))
          ),
          testError("failure")(
            StreamModuleMock.PolyErrorOutputStream.of[Int, Long](equalTo("foo"), value(failedStream[Int, Long](42))),
            StreamModule.polyErrorOutputStream("foo").flatMap(_.runCollect),
            equalTo(42)
          )
        ),
        testValue("combined")(
          StreamModuleMock.PolyErrorOutputStream
            .of[Long, Int](equalTo("foo"), value(successfulStream[Long, Int](1, 2))) andThen
            StreamModuleMock.PolyErrorOutputStream
              .of[Int, Long](equalTo("bar"), value(successfulStream[Int, Long](3L, 4L))),
          for {
            v1     <- StreamModule.polyErrorOutputStream[Long, Int]("foo")
            chunk1 <- v1.runCollect
            v2     <- StreamModule.polyErrorOutputStream[Int, Long]("bar")
            chunk2 <- v2.runCollect
          } yield (chunk1, chunk2),
          equalTo((Chunk(1, 2), Chunk(3L, 4L)))
        )
      )
    ),
    suite("polymorphic input, error and output")(
      suite("expectations met")(
        suite("String, Int, Long")(
          testValue("success")(
            StreamModuleMock.PolyInputErrorOutputStream
              .of[String, Int, Long](equalTo("foo"), value(successfulStream[Int, Long](1L, 3L, 5L))),
            StreamModule.polyInputErrorOutputStream("foo").flatMap(_.runCollect),
            equalTo(Chunk(1L, 3L, 5L))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputErrorOutputStream
              .of[String, Int, Long](equalTo("foo"), value(failedStream[Int, Long](42))),
            StreamModule.polyInputErrorOutputStream("foo").flatMap(_.runCollect),
            equalTo(42)
          )
        ),
        suite("Int, Long, String")(
          testValue("success")(
            StreamModuleMock.PolyInputErrorOutputStream
              .of[Int, Long, String](equalTo(42), value(successfulStream[Long, String]("foo", "bar"))),
            StreamModule.polyInputErrorOutputStream(42).flatMap(_.runCollect),
            equalTo(Chunk("foo", "bar"))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputErrorOutputStream
              .of[Int, Long, String](equalTo(42), value(failedStream[Long, String](42L))),
            StreamModule.polyInputErrorOutputStream(42).flatMap(_.runCollect),
            equalTo(42L)
          )
        ),
        suite("Long, String, Int")(
          testValue("success")(
            StreamModuleMock.PolyInputErrorOutputStream
              .of[Long, String, Int](equalTo(42L), value(successfulStream[String, Int](1, 2))),
            StreamModule.polyInputErrorOutputStream(42L).flatMap(_.runCollect),
            equalTo(Chunk(1, 2))
          ),
          testError("failure")(
            StreamModuleMock.PolyInputErrorOutputStream
              .of[Long, String, Int](equalTo(42L), value(failedStream[String, Int]("foo"))),
            StreamModule.polyInputErrorOutputStream(42L).flatMap(_.runCollect),
            equalTo("foo")
          )
        ),
        testValue("combined")(
          StreamModuleMock.PolyInputErrorOutputStream
            .of[String, Int, Long](equalTo("foo"), value(successfulStream[Int, Long](1L, 3L, 5L))) andThen
            StreamModuleMock.PolyInputErrorOutputStream
              .of[Int, Long, String](equalTo(42), value(successfulStream[Long, String]("foo", "bar"))) andThen
            StreamModuleMock.PolyInputErrorOutputStream
              .of[Long, String, Int](equalTo(42L), value(successfulStream[String, Int](4, 2))),
          for {
            v1     <- StreamModule.polyInputErrorOutputStream[String, Int, Long]("foo")
            chunk1 <- v1.runCollect
            v2     <- StreamModule.polyInputErrorOutputStream[Int, Long, String](42)
            chunk2 <- v2.runCollect
            v3     <- StreamModule.polyInputErrorOutputStream[Long, String, Int](42L)
            chunk3 <- v3.runCollect
          } yield (chunk1, chunk2, chunk3),
          equalTo((Chunk(1L, 3L, 5L), Chunk("foo", "bar"), Chunk(4, 2)))
        )
      )
    ),
    suite("polymorphic mixed output")(
      suite("expectations met")(
        testValue("String")(
          StreamModuleMock.PolyMixedStream
            .of[(String, String)](value(successfulStream[String, (String, String)]("foo" -> "bar", "baz" -> "qux"))),
          StreamModule.polyMixedStream[String].flatMap(_.runCollect),
          equalTo(Chunk("foo" -> "bar", "baz" -> "qux"))
        ),
        testValue("Int")(
          StreamModuleMock.PolyMixedStream
            .of[(Int, String)](value(successfulStream[String, (Int, String)](42 -> "bar"))),
          StreamModule.polyMixedStream[Int].flatMap(_.runCollect),
          equalTo(Chunk(42 -> "bar"))
        ),
        testValue("Long")(
          StreamModuleMock.PolyMixedStream
            .of[(Long, String)](value(successfulStream[String, (Long, String)](42L -> "bar"))),
          StreamModule.polyMixedStream[Long].flatMap(_.runCollect),
          equalTo(Chunk(42L -> "bar"))
        )
      )
    ) @@ exceptDotty,
    suite("polymorphic bounded output <: AnyVal")(
      suite("expectations met")(
        testValue("Double")(
          StreamModuleMock.PolyBoundedStream.of[Double](value(successfulStream[String, Double](42d))),
          StreamModule.polyBoundedStream[Double].flatMap(_.runCollect),
          equalTo(Chunk(42d))
        ),
        testValue("Int")(
          StreamModuleMock.PolyBoundedStream.of[Int](value(successfulStream[String, Int](42))),
          StreamModule.polyBoundedStream[Int].flatMap(_.runCollect),
          equalTo(Chunk(42))
        ),
        testValue("Long")(
          StreamModuleMock.PolyBoundedStream.of[Long](value(successfulStream[String, Long](42L))),
          StreamModule.polyBoundedStream[Long].flatMap(_.runCollect),
          equalTo(Chunk(42L))
        )
      )
    )
  )

}
