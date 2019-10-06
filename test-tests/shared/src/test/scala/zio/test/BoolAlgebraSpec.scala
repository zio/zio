package zio.test

import zio.test.Assertion._
import zio.test.BoolAlgebraSpecHelper._
import zio.test.TestAspect._

import scala.util.Random

object BoolAlgebraSpec
    extends ZIOBaseSpec(
      suite("BoolAlgebraSpec")(
        test("all returns conjunction of values") {
          assert(BoolAlgebra.all(List(success1, failure1, failure2)), isSome(isFailure))
        },
        test("and distributes over or") {
          val a = randomBoolAlgebra()
          val b = randomBoolAlgebra()
          val c = randomBoolAlgebra()
          assert(a && (b || c), equalTo((a && b) || (a && c)))
        } @@ nonFlaky(100),
        test("and is associative") {
          val a = randomBoolAlgebra()
          val b = randomBoolAlgebra()
          val c = randomBoolAlgebra()
          assert((a && b) && c, equalTo(a && (b && c)))
        } @@ nonFlaky(100),
        test("and is commutative") {
          val a = randomBoolAlgebra()
          val b = randomBoolAlgebra()
          assert(a && b, equalTo(b && a))
        } @@ nonFlaky(100),
        test("any returns disjunction of values") {
          assert(BoolAlgebra.any(List(success1, failure1, failure2)), isSome(isSuccess))
        },
        test("as maps values to constant value") {
          assert(
            (success1 && success2).as("value"),
            equalTo(BoolAlgebra.success("value") && BoolAlgebra.success("value"))
          )
        },
        test("both returns conjunction of two values") {
          assert(success1 && success2, isSuccess) &&
          assert(success1 && failure1, isFailure) &&
          assert(failure1 && success1, isFailure) &&
          assert(failure1 && failure2, isFailure)
        },
        test("collectAll combines multiple values") {
          assert(
            BoolAlgebra.collectAll(List(success1, failure1, failure2)),
            isSome(equalTo(success1 && failure1 && failure2))
          )
        },
        test("De Morgan's laws") {
          val a = randomBoolAlgebra()
          val b = randomBoolAlgebra()
          assert(!(a && b), equalTo(!a || !b)) &&
          assert(!a || !b, equalTo(!(a && b))) &&
          assert(!(a || b), equalTo(!a && !b)) &&
          assert(!a && !b, equalTo(!(a || b)))
        } @@ nonFlaky(100),
        test("double negative") {
          val a = randomBoolAlgebra()
          assert(!(!a), equalTo(a)) &&
          assert(a, equalTo(!(!a)))
        } @@ nonFlaky(100),
        test("either returns disjunction of two values") {
          assert(success1 || success2, isSuccess) &&
          assert(success1 || failure1, isSuccess) &&
          assert(failure1 || success1, isSuccess) &&
          assert(failure1 || failure2, isFailure)
        },
        test("hashCode is consistent with equals") {
          val (a, b) = randomEqualBoolAlgebra(4)
          assert(a.hashCode, equalTo(b.hashCode))
        } @@ nonFlaky(10),
        test("failures collects failures") {
          val actual   = (success1 && success2 && failure1 && failure2).failures.get
          val expected = !failure1 && !failure2
          assert(actual, equalTo(expected))
        },
        test("foreach combines multiple values") {
          def isEven(n: Int): BoolAlgebra[String] =
            if (n % 2 == 0) BoolAlgebra.success(s"$n is even")
            else BoolAlgebra.failure(s"$n is odd")

          val actual = BoolAlgebra.foreach(List(1, 2, 3))(isEven)
          val expected = BoolAlgebra.failure("1 is odd") &&
            BoolAlgebra.success("2 is even") &&
            BoolAlgebra.failure("3 is odd")

          assert(actual, isSome(equalTo(expected)))
        },
        test("implies returns implication of two values") {
          assert(success1 ==> success2, isSuccess) &&
          assert(success1 ==> failure1, isFailure) &&
          assert(failure1 ==> success1, isSuccess) &&
          assert(failure1 ==> failure2, isSuccess)
        },
        test("isFailure returns whether result is failure") {
          assert(!success1.isFailure && failure1.isFailure, isTrue)
        },
        test("isSuccess returns whether result is success") {
          assert(success1.isSuccess && !failure1.isSuccess, isTrue)
        },
        test("map transforms values") {
          val actual   = (success1 && failure1 && failure2).map(_.split(" ").head)
          val expected = BoolAlgebra.success("first") && BoolAlgebra.failure("first") && BoolAlgebra.failure("second")
          assert(actual, equalTo(expected))
        },
        test("or distributes over and") {
          val a = randomBoolAlgebra()
          val b = randomBoolAlgebra()
          val c = randomBoolAlgebra()

          val left  = a || (b && c)
          val right = (a || b) && (a || c)
          assert(left, equalTo(right))
        } @@ nonFlaky(100),
        test("or is associative") {
          val a     = randomBoolAlgebra()
          val b     = randomBoolAlgebra()
          val c     = randomBoolAlgebra()
          val left  = (a || b) || c
          val right = a || (b || c)
          assert(left, equalTo(right))
        } @@ nonFlaky(100),
        test("or is commutative") {
          val a = randomBoolAlgebra()
          val b = randomBoolAlgebra()
          assert(a || b, equalTo(b || a))
        } @@ nonFlaky(100)
      )
    )

object BoolAlgebraSpecHelper {
  val value1 = "first success"
  val value2 = "second success"
  val value3 = "first failure"
  val value4 = "second failure"

  val success1 = BoolAlgebra.success(value1)
  val success2 = BoolAlgebra.success(value2)
  val failure1 = BoolAlgebra.failure(value3)
  val failure2 = BoolAlgebra.failure(value4)

  val isSuccess: Assertion[BoolAlgebra[_]] = assertion("isSuccess")()(_.isSuccess)
  val isFailure: Assertion[BoolAlgebra[_]] = assertion("isFailure")()(_.isFailure)

  def randomBoolAlgebra(size: Int = Random.nextInt(10)): BoolAlgebra[Int] =
    if (size == 0) {
      BoolAlgebra.success(Random.nextInt(10))
    } else {
      val n = Random.nextInt(size)
      Random.nextInt(3) match {
        case 0 => randomBoolAlgebra(n) && randomBoolAlgebra(size - n - 1)
        case 1 => randomBoolAlgebra(n) || randomBoolAlgebra(size - n - 1)
        case 2 => !randomBoolAlgebra(size - 1)
      }
    }

  @scala.annotation.tailrec
  def randomEqualBoolAlgebra(size: Int = 3): (BoolAlgebra[Int], BoolAlgebra[Int]) = {
    val a = randomBoolAlgebra(size)
    val b = randomBoolAlgebra(size)
    if (a == b) (a, b) else randomEqualBoolAlgebra(size)
  }
}
