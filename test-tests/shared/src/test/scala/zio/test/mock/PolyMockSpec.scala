package zio.test.mock

import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.test.mock.module.{ PureModule, PureModuleMock }
import zio.test.{ suite, Assertion, TestAspect, ZIOBaseSpec }

object PolyMockSpec extends ZIOBaseSpec with MockSpecUtils[PureModule] {

  import Assertion._
  import Expectation._
  import InvalidCall._
  import MockException._
  import TestAspect.exceptDotty

  def spec = suite("PolyMockSpec")(
    suite("polymorphic input")(
      suite("expectations met")(
        testValue("String")(
          PureModuleMock.PolyInput.of[String](equalTo("foo"), value("bar")),
          PureModule.polyInput("foo"),
          equalTo("bar")
        ),
        testValue("Int")(
          PureModuleMock.PolyInput.of[Int](equalTo(42), value("bar")),
          PureModule.polyInput(42),
          equalTo("bar")
        ),
        testValue("Long")(
          PureModuleMock.PolyInput.of[Long](equalTo(42L), value("bar")),
          PureModule.polyInput(42L),
          equalTo("bar")
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, Long, Int, String, String, String, String]
          type M1 = Capability[PureModule, Long, String, String]
          type M2 = Capability[PureModule, Int, String, String]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyInput.of[Long](equalTo(42L), value("bar")),
            PureModule.polyInput(42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
        testError("String")(
          PureModuleMock.PolyError.of[String](equalTo("foo"), failure("bar")),
          PureModule.polyError("foo"),
          equalTo("bar")
        ),
        testError("Int")(
          PureModuleMock.PolyError.of[Int](equalTo("foo"), failure(42)),
          PureModule.polyError("foo"),
          equalTo(42)
        ),
        testError("Long")(
          PureModuleMock.PolyError.of[Long](equalTo("foo"), failure(42L)),
          PureModule.polyError("foo"),
          equalTo(42L)
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, String, String, Long, Int, String, String]
          type M1 = Capability[PureModule, String, Long, String]
          type M2 = Capability[PureModule, String, Int, String]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyError.of[Long](equalTo("foo"), failure(42L)),
            PureModule.polyError[Int]("foo"),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
        testValue("String")(
          PureModuleMock.PolyOutput.of[String](equalTo("foo"), value("bar")),
          PureModule.polyOutput("foo"),
          equalTo("bar")
        ),
        testValue("Int")(
          PureModuleMock.PolyOutput.of[Int](equalTo("foo"), value(42)),
          PureModule.polyOutput("foo"),
          equalTo(42)
        ),
        testValue("Long")(
          PureModuleMock.PolyOutput.of[Long](equalTo("foo"), value(42L)),
          PureModule.polyOutput("foo"),
          equalTo(42L)
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, String, String, String, String, Long, Int]
          type M1 = Capability[PureModule, String, String, Long]
          type M2 = Capability[PureModule, String, String, Int]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyOutput.of[Long](equalTo("foo"), value(42L)),
            PureModule.polyOutput[Int]("foo"),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
          testValue("success")(
            PureModuleMock.PolyInputError.of[Long, Int](equalTo(42L), value("foo")),
            PureModule.polyInputError(42L),
            equalTo("foo")
          ),
          testError("failure")(
            PureModuleMock.PolyInputError.of[Long, Int](equalTo(42L), failure(42)),
            PureModule.polyInputError(42L),
            equalTo(42)
          )
        ),
        suite("Int, Long")(
          testValue("success")(
            PureModuleMock.PolyInputError.of[Int, Long](equalTo(42), value("foo")),
            PureModule.polyInputError(42),
            equalTo("foo")
          ),
          testError("failure")(
            PureModuleMock.PolyInputError.of[Int, Long](equalTo(42), failure(42L)),
            PureModule.polyInputError(42),
            equalTo(42L)
          )
        ),
        testValue("combined")(
          PureModuleMock.PolyInputError.of[Long, Int](equalTo(42L), value("foo")) andThen
            PureModuleMock.PolyInputError.of[Int, Long](equalTo(42), value("bar")),
          for {
            v1 <- PureModule.polyInputError[Long, Int](42L)
            v2 <- PureModule.polyInputError[Int, Long](42)
          } yield (v1, v2),
          equalTo(("foo", "bar"))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, Long, Int, Int, Long, String, String]
          type M1 = Capability[PureModule, Long, Int, String]
          type M2 = Capability[PureModule, Int, Long, String]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyInputError.of[Long, Int](equalTo(42L), value("foo")),
            PureModule.polyInputError[Int, Long](42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
          testValue("success")(
            PureModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), value(42)),
            PureModule.polyInputOutput(42L),
            equalTo(42)
          ),
          testError("failure")(
            PureModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), failure("foo")),
            PureModule.polyInputOutput(42L),
            equalTo("foo")
          )
        ),
        suite("Int, Long")(
          testValue("success")(
            PureModuleMock.PolyInputOutput.of[Int, Long](equalTo(42), value(42L)),
            PureModule.polyInputOutput(42),
            equalTo(42L)
          ),
          testError("failure")(
            PureModuleMock.PolyInputOutput.of[Int, Long](equalTo(42), failure("foo")),
            PureModule.polyInputOutput(42),
            equalTo("foo")
          )
        ),
        testValue("combined")(
          PureModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), value(42)) andThen
            PureModuleMock.PolyInputOutput.of[Int, Long](equalTo(42), value(42L)),
          for {
            v1 <- PureModule.polyInputOutput[Long, Int](42L)
            v2 <- PureModule.polyInputOutput[Int, Long](42)
          } yield (v1, v2),
          equalTo((42, 42L))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, Long, Int, String, String, Int, Long]
          type M1 = Capability[PureModule, Long, String, Int]
          type M2 = Capability[PureModule, Int, String, Long]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyInputOutput.of[Long, Int](equalTo(42L), value(42)),
            PureModule.polyInputOutput[Int, Long](42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
          testValue("success")(
            PureModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), value(42)),
            PureModule.polyErrorOutput("foo"),
            equalTo(42)
          ),
          testError("failure")(
            PureModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), failure(42L)),
            PureModule.polyErrorOutput("foo"),
            equalTo(42L)
          )
        ),
        suite("Int, Long")(
          testValue("success")(
            PureModuleMock.PolyErrorOutput.of[Int, Long](equalTo("foo"), value(42L)),
            PureModule.polyErrorOutput("foo"),
            equalTo(42L)
          ),
          testError("failure")(
            PureModuleMock.PolyErrorOutput.of[Int, Long](equalTo("foo"), failure(42)),
            PureModule.polyErrorOutput("foo"),
            equalTo(42)
          )
        ),
        testValue("combined")(
          PureModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), value(42)) andThen
            PureModuleMock.PolyErrorOutput.of[Int, Long](equalTo("bar"), value(42L)),
          for {
            v1 <- PureModule.polyErrorOutput[Long, Int]("foo")
            v2 <- PureModule.polyErrorOutput[Int, Long]("bar")
          } yield (v1, v2),
          equalTo((42, 42L))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, String, String, Long, Int, Int, Long]
          type M1 = Capability[PureModule, String, Long, Int]
          type M2 = Capability[PureModule, String, Int, Long]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyErrorOutput.of[Long, Int](equalTo("foo"), value(42)),
            PureModule.polyErrorOutput[Int, Long]("foo"),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
          testValue("success")(
            PureModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), value(42L)),
            PureModule.polyInputErrorOutput("foo"),
            equalTo(42L)
          ),
          testError("failure")(
            PureModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), failure(42)),
            PureModule.polyInputErrorOutput("foo"),
            equalTo(42L)
          )
        ),
        suite("Int, Long, String")(
          testValue("success")(
            PureModuleMock.PolyInputErrorOutput.of[Int, Long, String](equalTo(42), value("foo")),
            PureModule.polyInputErrorOutput(42),
            equalTo("foo")
          ),
          testError("failure")(
            PureModuleMock.PolyInputErrorOutput.of[Int, Long, String](equalTo(42), failure(42L)),
            PureModule.polyInputErrorOutput(42),
            equalTo(42L)
          )
        ),
        suite("Long, String, Int")(
          testValue("success")(
            PureModuleMock.PolyInputErrorOutput.of[Long, String, Int](equalTo(42L), value(42)),
            PureModule.polyInputErrorOutput(42L),
            equalTo(42)
          ),
          testError("failure")(
            PureModuleMock.PolyInputErrorOutput.of[Long, String, Int](equalTo(42L), failure("foo")),
            PureModule.polyInputErrorOutput(42L),
            equalTo("foo")
          )
        ),
        testValue("combined")(
          PureModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), value(42L)) andThen
            PureModuleMock.PolyInputErrorOutput.of[Int, Long, String](equalTo(42), value("foo")) andThen
            PureModuleMock.PolyInputErrorOutput.of[Long, String, Int](equalTo(42L), value(42)),
          for {
            v1 <- PureModule.polyInputErrorOutput[String, Int, Long]("foo")
            v2 <- PureModule.polyInputErrorOutput[Int, Long, String](42)
            v3 <- PureModule.polyInputErrorOutput[Long, String, Int](42L)
          } yield (v1, v2, v3),
          equalTo((42L, "foo", 42))
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, Int, String, Long, Int, String, Long]
          type M1 = Capability[PureModule, Int, Long, String]
          type M2 = Capability[PureModule, String, Int, Long]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyInputErrorOutput.of[String, Int, Long](equalTo("foo"), value(42L)),
            PureModule.polyInputErrorOutput[Int, Long, String](42),
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
        testValue("String")(
          PureModuleMock.PolyMixed.of[(String, String)](value("bar" -> "baz")),
          PureModule.polyMixed[String],
          equalTo("bar" -> "baz")
        ),
        testValue("Int")(
          PureModuleMock.PolyMixed.of[(Int, String)](value(42 -> "bar")),
          PureModule.polyMixed[Int],
          equalTo(42 -> "bar")
        ),
        testValue("Long")(
          PureModuleMock.PolyMixed.of[(Long, String)](value(42L -> "bar")),
          PureModule.polyMixed[Long],
          equalTo(42L -> "bar")
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, Unit, Unit, String, String, (Int, String), (Long, String)]
          type M1 = Capability[PureModule, Unit, String, (Int, String)]
          type M2 = Capability[PureModule, Unit, String, (Long, String)]
          testDied("invalid polymorphic type")(
            PureModuleMock.PolyMixed.of[(Long, String)](value(42L -> "bar")),
            PureModule.polyMixed[Int],
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
        testValue("Double")(
          PureModuleMock.PolyBounded.of[Double](value(42d)),
          PureModule.polyBounded[Double],
          equalTo(42d)
        ),
        testValue("Int")(
          PureModuleMock.PolyBounded.of[Int](value(42)),
          PureModule.polyBounded[Int],
          equalTo(42)
        ),
        testValue("Long")(
          PureModuleMock.PolyBounded.of[Long](value(42L)),
          PureModule.polyBounded[Long],
          equalTo(42L)
        )
      ),
      suite("expectations failed")(
        {
          type E  = InvalidPolyType[PureModule, PureModule, Unit, Unit, String, String, Int, Long]
          type M1 = Capability[PureModule, Unit, String, Int]
          type M2 = Capability[PureModule, Unit, String, Long]

          testDied("invalid polymorphic type")(
            PureModuleMock.PolyBounded.of[Long](value(42L)),
            PureModule.polyBounded[Int],
            isSubtype[InvalidCallException](
              hasField[InvalidCallException, List[InvalidCall]](
                "failedMatches",
                _.failedMatches,
                hasFirst(
                  isSubtype[E](
                    hasField[E, M1]("invoked", _.invoked, anything) &&
                      hasField[E, M2]("expected", _.expected, anything)
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
