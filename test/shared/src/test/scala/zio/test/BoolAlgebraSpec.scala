package zio.test

import scala.concurrent.Future
import scala.util.Random

import zio.test.TestUtils.label

object BoolAlgebraSpec extends ZIOBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(allReturnsConjunctionOfValues, "all returns conjunction of values"),
    label(andDistributesOverOr, "and distributes over or"),
    label(andIsAssociative, "and is associative"),
    label(andIsCommutative, "and is commutative"),
    label(anyReturnsDisjunctionOfValues, "any returns disjunction of values"),
    label(asMapsValuesToConstantValue, "as maps values to constant value"),
    label(bothReturnsConjunctionOfTwoValues, "both returns conjunction of two values"),
    label(collectAllCombinesMultipleValues, "collectAll combines multiple values"),
    label(deMorgansLaws, "De Morgan's laws"),
    label(doubleNegative, "double negative"),
    label(eitherReturnsDisjunctionOfTwoValues, "either returns disjunction of two values"),
    label(hashCodeIsConsistentWithEquals, "hashCode is consistent with equals"),
    label(failuresCollectsFailures, "failures collects failures"),
    label(foreachCombinesMultipleValues, "foreach combines multiple values"),
    label(impliesReturnsImplicationOfTwoValues, "implies returns implication of two values"),
    label(isFailureReturnsWhetherResultIsFailure, "isFailure returns whether result is failure"),
    label(isSuccessReturnsWhetherResultIsSuccess, "isSuccess returns whether result is success"),
    label(mapTransformsValues, "map transforms values"),
    label(orDistributesOverAnd, "or distributes over and"),
    label(orIsAssociative, "or is associative"),
    label(orIsCommutative, "or is commutative")
  )

  val value1 = "first success"
  val value2 = "second success"
  val value3 = "first failure"
  val value4 = "second failure"

  val success1 = BoolAlgebra.success(value1)
  val success2 = BoolAlgebra.success(value2)
  val failure1 = BoolAlgebra.failure(value3)
  val failure2 = BoolAlgebra.failure(value4)

  def allReturnsConjunctionOfValues: Future[Boolean] =
    Future.successful(BoolAlgebra.all(List(success1, failure1, failure2)).get.isFailure)

  def andDistributesOverOr: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      val c = randomBoolAlgebra()
      (a && (b || c)) == ((a && b) || (a && c))
    }

  def andIsAssociative: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      val c = randomBoolAlgebra()
      ((a && b) && c) == (a && (b && c))
    }

  def andIsCommutative: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      (a && b) == (b && a)
    }

  def anyReturnsDisjunctionOfValues: Future[Boolean] =
    Future.successful(BoolAlgebra.any(List(success1, failure1, failure2)).get.isSuccess)

  def asMapsValuesToConstantValue: Future[Boolean] =
    Future.successful {
      (success1 && success2).as("value") ==
        (BoolAlgebra.success("value") && BoolAlgebra.success("value"))
    }

  def bothReturnsConjunctionOfTwoValues: Future[Boolean] =
    Future.successful {
      (success1 && success2).isSuccess &&
      (success1 && failure1).isFailure &&
      (failure1 && success1).isFailure &&
      (failure1 && failure2).isFailure
    }

  def collectAllCombinesMultipleValues: Future[Boolean] =
    Future.successful {
      BoolAlgebra.collectAll(List(success1, failure1, failure2)) ==
        Some((success1 && failure1 && failure2))
    }

  def doubleNegative: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      (!(!a) == a) && a == !(!a)
    }

  def deMorgansLaws: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      (!(a && b) == (!a || !b)) &&
      ((!a || !b) == !(a && b)) &&
      (!(a || b) == (!a && !b)) &&
      ((!a && !b) == !(a || b))
    }

  def eitherReturnsDisjunctionOfTwoValues: Future[Boolean] =
    Future.successful {
      (success1 || success2).isSuccess &&
      (success1 || failure1).isSuccess &&
      (failure1 || success1).isSuccess &&
      (failure1 || failure2).isFailure
    }

  def failuresCollectsFailures: Future[Boolean] =
    Future.successful {
      val actual   = (success1 && success2 && failure1 && failure2).failures.get
      val expected = !failure1 && !failure2
      actual == expected
    }

  def foreachCombinesMultipleValues: Future[Boolean] =
    Future.successful {
      def isEven(n: Int): BoolAlgebra[String] =
        if (n % 2 == 0) BoolAlgebra.success(s"$n is even")
        else BoolAlgebra.failure(s"$n is odd")
      BoolAlgebra.foreach(List(1, 2, 3))(isEven) ==
        Some {
          BoolAlgebra.failure("1 is odd") &&
          BoolAlgebra.success("2 is even") &&
          BoolAlgebra.failure("3 is odd")
        }
    }

  def hashCodeIsConsistentWithEquals: Future[Boolean] =
    Future.successful {
      (1 to 10).forall { _ =>
        val (a, b) = randomEqualBoolAlgebra(4)
        a.hashCode == b.hashCode
      }
    }

  def impliesReturnsImplicationOfTwoValues: Future[Boolean] =
    Future.successful {
      (success1 ==> success2).isSuccess &&
      (success1 ==> failure1).isFailure &&
      (failure1 ==> success1).isSuccess &&
      (failure1 ==> failure2).isSuccess
    }

  def isFailureReturnsWhetherResultIsFailure: Future[Boolean] =
    Future.successful(!success1.isFailure && failure1.isFailure)

  def isSuccessReturnsWhetherResultIsSuccess: Future[Boolean] =
    Future.successful(success1.isSuccess && !failure1.isSuccess)

  def mapTransformsValues: Future[Boolean] =
    Future.successful {
      val actual   = (success1 && failure1 && failure2).map(_.split(" ").head)
      val expected = BoolAlgebra.success("first") && BoolAlgebra.failure("first") && BoolAlgebra.failure("second")
      actual == expected
    }

  def orDistributesOverAnd: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      val c = randomBoolAlgebra()
      (a || (b && c)) == ((a || b) && (a || c))
    }

  def orIsAssociative: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      val c = randomBoolAlgebra()
      ((a || b) || c) == (a || (b || c))
    }

  def orIsCommutative: Future[Boolean] =
    forall {
      val a = randomBoolAlgebra()
      val b = randomBoolAlgebra()
      (a || b) == (b || a)
    }

  def forall(p: => Boolean): Future[Boolean] =
    Future.successful((1 to 100).forall(_ => p))

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

  def randomEqualBoolAlgebra(size: Int = 3): (BoolAlgebra[Int], BoolAlgebra[Int]) = {
    val a = randomBoolAlgebra(size)
    val b = randomBoolAlgebra(size)
    if (a == b) (a, b) else randomEqualBoolAlgebra(size)
  }
}
