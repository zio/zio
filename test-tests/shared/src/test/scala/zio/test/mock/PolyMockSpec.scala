package zio.test.mock

import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.test.mock.module.{ Module, ModuleMock }
import zio.test.{ suite, Assertion, TestAspect, ZIOBaseSpec }

object PolyMockSpec extends ZIOBaseSpec with MockSpecUtils {

  import Assertion._
  import Expectation._
  import InvalidCall._
  import MockException._
  import TestAspect.exceptDotty

  def spec = suite("PolyMockSpec")(
    suite("polymorphic input")(
      suite("expectations met")(
        testSpec("String")(
          ModuleMock.PolyInput.of[String](equalTo("foo"), value("bar")),
          Module.polyInput("foo"),
          equalTo("bar")
        ),
        testSpec("Int")(
          ModuleMock.PolyInput.of[Int](equalTo(42), value("bar")),
          Module.polyInput(42),
          equalTo("bar")
        ),
        testSpec("Long")(
          ModuleMock.PolyInput.of[Long](equalTo(42L), value("bar")),
          Module.polyInput(42L),
          equalTo("bar")
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, Long, Int, String, String, String, String]
          type M1 = Method[Module, Long, String, String]
          type M2 = Method[Module, Int, String, String]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyInput.of[Long](equalTo(42L), value("bar")),
            Module.polyInput(42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    suite("polymorphic error")(
      suite("expectations met")(
        testSpec("String")(
          ModuleMock.PolyError.of[String](equalTo("foo"), failure("bar")),
          Module.polyError("foo").flip,
          equalTo("bar")
        ),
        testSpec("Int")(
          ModuleMock.PolyError.of[Int](equalTo("foo"), failure(42)),
          Module.polyError("foo").flip,
          equalTo(42)
        ),
        testSpec("Long")(
          ModuleMock.PolyError.of[Long](equalTo("foo"), failure(42L)),
          Module.polyError("foo").flip,
          equalTo(42L)
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, String, String, Long, Int, String, String]
          type M1 = Method[Module, String, Long, String]
          type M2 = Method[Module, String, Int, String]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyError.of[Long](equalTo("foo"), failure(42L)),
            Module.polyError[Int]("foo"),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    suite("polymorphic value")(
      suite("expectations met")(
        testSpec("String")(
          ModuleMock.PolyOutput.of[String](equalTo("foo"), value("bar")),
          Module.polyOutput("foo"),
          equalTo("bar")
        ),
        testSpec("Int")(
          ModuleMock.PolyOutput.of[Int](equalTo("foo"), value(42)),
          Module.polyOutput("foo"),
          equalTo(42)
        ),
        testSpec("Long")(
          ModuleMock.PolyOutput.of[Long](equalTo("foo"), value(42L)),
          Module.polyOutput("foo"),
          equalTo(42L)
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, String, String, String, String, Long, Int]
          type M1 = Method[Module, String, String, Long]
          type M2 = Method[Module, String, String, Int]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyOutput.of[Long](equalTo("foo"), value(42L)),
            Module.polyOutput[Int]("foo"),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    suite("polymorphic input and error")(
      suite("expectations met")(
        suite("Long, Int")(
          testSpec("success")(
            ModuleMock.PolyInputError.of[Long, Int](equalTo(42L), value("foo")),
            Module.polyInputError(42L),
            equalTo("foo")
          ),
          testSpec("failure")(
            ModuleMock.PolyInputError.of[Long, Int](equalTo(42L), failure(42)),
            Module.polyInputError(42L).flip,
            equalTo(42)
          )
        ),
        suite("Int, Long")(
          testSpec("success")(
            ModuleMock.PolyInputError.of[Int, Long](equalTo(42), value("foo")),
            Module.polyInputError(42),
            equalTo("foo")
          ),
          testSpec("failure")(
            ModuleMock.PolyInputError.of[Int, Long](equalTo(42), failure(42L)),
            Module.polyInputError(42).flip,
            equalTo(42L)
          )
        ),
        testSpec("combined")(
          ModuleMock.PolyInputError.of[Long, Int](equalTo(42L), value("foo")) andThen
            ModuleMock.PolyInputError.of[Int, Long](equalTo(42), value("bar")),
          for {
            v1 <- Module.polyInputError[Long, Int](42L)
            v2 <- Module.polyInputError[Int, Long](42)
          } yield (v1, v2),
          equalTo(("foo", "bar"))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, Long, Int, Int, Long, String, String]
          type M1 = Method[Module, Long, Int, String]
          type M2 = Method[Module, Int, Long, String]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyInputError.of[Long, Int](equalTo(42L), value("foo")),
            Module.polyInputError[Int, Long](42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    suite("polymorphic input and output")(
      suite("expectations met")(
        suite("Long, Int")(
          testSpec("success")(
            ModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), value(42)),
            Module.polyInputOutput(42L),
            equalTo(42)
          ),
          testSpec("failure")(
            ModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), failure("foo")),
            Module.polyInputOutput(42L).flip,
            equalTo("foo")
          )
        ),
        suite("Int, Long")(
          testSpec("success")(
            ModuleMock.PolyInputOutput.of[Int, Long](equalTo(42), value(42L)),
            Module.polyInputOutput(42),
            equalTo(42L)
          ),
          testSpec("failure")(
            ModuleMock.PolyInputOutput.of[Int, Long](equalTo(42), failure("foo")),
            Module.polyInputOutput(42).flip,
            equalTo("foo")
          )
        ),
        testSpec("combined")(
          ModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), value(42)) andThen
            ModuleMock.PolyInputOutput.of[Int, Long](equalTo(42), value(42L)),
          for {
            v1 <- Module.polyInputOutput[Long, Int](42L)
            v2 <- Module.polyInputOutput[Int, Long](42)
          } yield (v1, v2),
          equalTo((42, 42L))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, Long, Int, String, String, Int, Long]
          type M1 = Method[Module, Long, String, Int]
          type M2 = Method[Module, Int, String, Long]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), value(42)),
            Module.polyInputOutput[Int, Long](42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    suite("polymorphic error and output")(
      suite("expectations met")(
        suite("Long, Int")(
          testSpec("success")(
            ModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), value(42)),
            Module.polyErrorOutput("foo"),
            equalTo(42)
          ),
          testSpec("failure")(
            ModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), failure(42L)),
            Module.polyErrorOutput("foo").flip,
            equalTo(42L)
          )
        ),
        suite("Int, Long")(
          testSpec("success")(
            ModuleMock.PolyErrorOutput.of[Int, Long](equalTo("foo"), value(42L)),
            Module.polyErrorOutput("foo"),
            equalTo(42L)
          ),
          testSpec("failure")(
            ModuleMock.PolyErrorOutput.of[Int, Long](equalTo("foo"), failure(42)),
            Module.polyErrorOutput("foo").flip,
            equalTo(42)
          )
        ),
        testSpec("combined")(
          ModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), value(42)) andThen
            ModuleMock.PolyErrorOutput.of[Int, Long](equalTo("bar"), value(42L)),
          for {
            v1 <- Module.polyErrorOutput[Long, Int]("foo")
            v2 <- Module.polyErrorOutput[Int, Long]("bar")
          } yield (v1, v2),
          equalTo((42, 42L))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, String, String, Long, Int, Int, Long]
          type M1 = Method[Module, String, Long, Int]
          type M2 = Method[Module, String, Int, Long]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), value(42)),
            Module.polyErrorOutput[Int, Long]("foo"),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    suite("polymorphic input, error and value")(
      suite("expectations met")(
        suite("String, Int, Long")(
          testSpec("success")(
            ModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), value(42L)),
            Module.polyInputErrorOutput("foo"),
            equalTo(42L)
          ),
          testSpec("failure")(
            ModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), failure(42)),
            Module.polyInputErrorOutput("foo").flip,
            equalTo(42L)
          )
        ),
        suite("Int, Long, String")(
          testSpec("success")(
            ModuleMock.PolyInputErrorOutput.of[Int, Long, String](equalTo(42), value("foo")),
            Module.polyInputErrorOutput(42),
            equalTo("foo")
          ),
          testSpec("failure")(
            ModuleMock.PolyInputErrorOutput.of[Int, Long, String](equalTo(42), failure(42L)),
            Module.polyInputErrorOutput(42).flip,
            equalTo(42L)
          )
        ),
        suite("Long, String, Int")(
          testSpec("success")(
            ModuleMock.PolyInputErrorOutput.of[Long, String, Int](equalTo(42L), value(42)),
            Module.polyInputErrorOutput(42L),
            equalTo(42)
          ),
          testSpec("failure")(
            ModuleMock.PolyInputErrorOutput.of[Long, String, Int](equalTo(42L), failure("foo")),
            Module.polyInputErrorOutput(42L).flip,
            equalTo("foo")
          )
        ),
        testSpec("combined")(
          ModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), value(42L)) andThen
            ModuleMock.PolyInputErrorOutput.of[Int, Long, String](equalTo(42), value("foo")) andThen
            ModuleMock.PolyInputErrorOutput.of[Long, String, Int](equalTo(42L), value(42)),
          for {
            v1 <- Module.polyInputErrorOutput[String, Int, Long]("foo")
            v2 <- Module.polyInputErrorOutput[Int, Long, String](42)
            v3 <- Module.polyInputErrorOutput[Long, String, Int](42L)
          } yield (v1, v2, v3),
          equalTo((42L, "foo", 42))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, Int, String, Long, Int, String, Long]
          type M1 = Method[Module, Int, Long, String]
          type M2 = Method[Module, String, Int, Long]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), value(42L)),
            Module.polyInputErrorOutput[Int, Long, String](42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ),
    // assignability and subclassing are not the same concepts
    // and so the `ClassTag` based implementation for Tagged is broken
    // on dotty for some cases (like Tuples, Lists, etc)
    // see https://github.com/zio/zio/pull/3136
    // will be fixed when izumi-reflect is supported on dotty
    suite("polymorphic mixed output")(
      suite("expectations met")(
        testSpec("String")(
          ModuleMock.PolyMixed.of[(String, String)](value("bar" -> "baz")),
          Module.polyMixed[String],
          equalTo("bar" -> "baz")
        ),
        testSpec("Int")(
          ModuleMock.PolyMixed.of[(Int, String)](value(42 -> "bar")),
          Module.polyMixed[Int],
          equalTo(42 -> "bar")
        ),
        testSpec("Long")(
          ModuleMock.PolyMixed.of[(Long, String)](value(42L -> "bar")),
          Module.polyMixed[Long],
          equalTo(42L -> "bar")
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, Unit, Unit, String, String, (Int, String), (Long, String)]
          type M1 = Method[Module, Unit, String, (Int, String)]
          type M2 = Method[Module, Unit, String, (Long, String)]
          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyMixed.of[(Long, String)](value(42L -> "bar")),
            Module.polyMixed[Int],
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    ) @@ exceptDotty,
    suite("polymorphic bounded output <: AnyVal")(
      suite("expectations met")(
        testSpec("Double")(
          ModuleMock.PolyBounded.of[Double](value(42d)),
          Module.polyBounded[Double],
          equalTo(42d)
        ),
        testSpec("Int")(
          ModuleMock.PolyBounded.of[Int](value(42)),
          Module.polyBounded[Int],
          equalTo(42)
        ),
        testSpec("Long")(
          ModuleMock.PolyBounded.of[Long](value(42L)),
          Module.polyBounded[Long],
          equalTo(42L)
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[Module, Module, Unit, Unit, String, String, Int, Long]
          type M1 = Method[Module, Unit, String, Int]
          type M2 = Method[Module, Unit, String, Long]

          testSpecDied("invalid polymorphic type")(
            ModuleMock.PolyBounded.of[Long](value(42L)),
            Module.polyBounded[Int],
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("method", _.method, anything) &&
                      hasField[E, M2]("expectedMethod", _.expectedMethod, anything)
                  )
                )
              )
            )
          )
        }
      )
    )
  )
}
