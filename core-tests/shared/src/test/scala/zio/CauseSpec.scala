package zio

import zio.Cause.{ Both, Then }
import zio.random.Random
import zio.test._
import zio.test.Assertion._

import zio.CauseSpecUtil._

object CauseSpec
    extends ZIOBaseSpec(
      suite("CauseSpec")(
        suite("Cause")(
          testM("`Cause#died` and `Cause#stripFailures` are consistent") {
            check(causes) { c =>
              assert(c.stripFailures, if (c.died) isSome(anything) else isNone)
            }
          },
          testM("`Cause.equals` is symmetric") {
            check(causes, causes) { (a, b) =>
              assert(a == b, equalTo(b == a))
            }
          },
          testM("`Cause.equals` and `Cause.hashCode` satisfy the contract") {
            check(equalCauses) {
              case (a, b) =>
                assert(a.hashCode, equalTo(b.hashCode))
            }
          },
          testM("`Cause#untraced` removes all traces") {
            check(causes) { c =>
              assert(c.untraced.traces.headOption, isNone)
            }
          },
          zio.test.test("`Cause.failures is stack safe") {
            val n     = 100000
            val cause = List.fill(n)(Cause.fail("fail")).reduce(_ && _)
            assert(cause.failures.length, equalTo(n))
          }
        ),
        suite("Then")(
          testM("`Then.equals` satisfies associativity") {
            check(causes, causes, causes) { (a, b, c) =>
              assert(Then(Then(a, b), c), equalTo(Then(a, Then(b, c)))) &&
              assert(Then(a, Then(b, c)), equalTo(Then(Then(a, b), c)))
            }
          },
          testM("`Then.equals` satisfies distributivity") {
            check(causes, causes, causes) { (a, b, c) =>
              assert(Then(a, Both(b, c)), equalTo(Both(Then(a, b), Then(a, c)))) &&
              assert(Then(Both(a, b), c), equalTo(Both(Then(a, c), Then(b, c))))
            }
          }
        ),
        suite("Both")(
          testM("`Both.equals` satisfies associativity") {
            check(causes, causes, causes) { (a, b, c) =>
              assert(Both(Both(a, b), c), equalTo(Both(a, Both(b, c)))) &&
              assert(Both(a, Both(b, c)), equalTo(Both(Both(a, b), c)))
            }
          },
          testM("`Both.equals` satisfies distributivity") {
            check(causes, causes, causes) { (a, b, c) =>
              assert(Both(Then(a, b), Then(a, c)), equalTo(Then(a, Both(b, c)))) &&
              assert(Both(Then(a, c), Then(b, c)), equalTo(Then(Both(a, b), c)))
            }
          },
          testM("`Both.equals` satisfies commutativity") {
            check(causes, causes) { (a, b) =>
              assert(Both(a, b), equalTo(Both(b, a)))
            }
          }
        ),
        suite("Meta")(
          testM("`Meta` is excluded from equals") {
            check(causes) { c =>
              assert(Cause.stackless(c), equalTo(c)) &&
              assert(c, equalTo(Cause.stackless(c)))
            }
          },
          testM("`Meta` is excluded from hashCode") {
            check(causes) { c =>
              assert(Cause.stackless(c).hashCode, equalTo(c.hashCode))
            }
          }
        ),
        suite("Monad Laws:")(
          testM("Left identity") {
            check(causes) { c =>
              assert(c.flatMap(Cause.fail), equalTo(c))
            }
          },
          testM("Right identity") {
            check(errors, errorCauseFunctions) { (e, f) =>
              assert(Cause.fail(e).flatMap(f), equalTo(f(e)))
            }
          },
          testM("Associativity") {
            check(causes, errorCauseFunctions, errorCauseFunctions) { (c, f, g) =>
              assert(c.flatMap(f).flatMap(g), equalTo(c.flatMap(e => f(e).flatMap(g))))
            }
          }
        )
      )
    )

object CauseSpecUtil {

  val causes: Gen[Random with Sized, Cause[String]] =
    Gen.causes(Gen.anyString, Gen.anyString.map(s => new RuntimeException(s)))

  val equalCauses: Gen[Random with Sized, (Cause[String], Cause[String])] =
    (causes <*> causes <*> causes).flatMap {
      case ((a, b), c) =>
        val fiberId = Fiber.Id(0L, 0L)
        Gen.elements(
          (a, a),
          (a, Cause.traced(a, ZTrace(fiberId, Nil, Nil, None))),
          (Then(Then(a, b), c), Then(a, Then(b, c))),
          (Then(a, Both(b, c)), Both(Then(a, b), Then(a, c))),
          (Both(Both(a, b), c), Both(a, Both(b, c))),
          (Both(Then(a, c), Then(b, c)), Then(Both(a, b), c)),
          (Both(a, b), Both(b, a)),
          (a, Cause.stackless(a))
        )
    }

  val errorCauseFunctions: Gen[Random with Sized, String => Cause[String]] =
    Gen.function(causes)

  val errors: Gen[Random with Sized, String] =
    Gen.anyString
}
