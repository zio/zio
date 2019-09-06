package zio.test

import scala.concurrent.Future
import scala.util.Random

import zio.DefaultRuntime
import zio.test.TestUtils.label

object AssertResultSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(allReturnsConjunctionOfAssertResults, "all returns conjunction of assert results"),
    label(andDistributesOverOr, "and distributes over or"),
    label(andIsAssociative, "and is associative"),
    label(andIsCommutative, "and is commutative"),
    label(anyReturnsDisjunctionOfAssertResults, "any returns disjunction of assert results"),
    label(asMapsValuesToConstantValue, "as maps values to constant value"),
    label(bothReturnsConjunctionOfTwoAssertResults, "both returns conjunction of two assert results"),
    label(collectAllCombinesMultipleAssertResults, "collectAll combines multiple assert results"),
    label(eitherReturnsDisjunctionOfTwoAssertResults, "either returns disjunction of two assert results"),
    label(hashCodeIsConsistentWithEquals, "hashCode is consistent with equals"),
    label(failuresCollectsFailedAssertResults, "failures returns list of failures"),
    label(foreachCombinesMultipleassertResults, "foreach combines multiple assert results"),
    label(isFailureReturnsWhetherAssertResultFailed, "isFailure returns whether assert result failed"),
    label(isSuccessReturnsWhetherAssertResultSucceeded, "isSuccess returns whether assert result succeeded"),
    label(mapTransformsValues, "map transforms values"),
    label(notNegatesAssertResults, "not negates assert results"),
    label(orDistributesOverAnd, "or distributes over and"),
    label(orIsAssociative, "or is associative"),
    label(orIsCommutative, "or is commutative")
  )

  val message1 = "first success"
  val message2 = "second success"
  val message3 = "first failure"
  val message4 = "second failure"

  val success1 = AssertResult.success(message1)
  val success2 = AssertResult.success(message2)
  val failure1 = AssertResult.failure(message3)
  val failure2 = AssertResult.failure(message4)
  val value1   = AssertResult.value(message3)
  val value2   = AssertResult.value(message4)

  def allReturnsConjunctionOfAssertResults: Future[Boolean] =
    Future.successful(AssertResult.all(List(success1, failure1, failure2)).get.isFailure)

  def andDistributesOverOr: Future[Boolean] =
    forall {
      val assertResult1 = randomAssertResult()
      val assertResult2 = randomAssertResult()
      val assertResult3 = randomAssertResult()
      (assertResult1 && (assertResult2 || assertResult3)) ==
        ((assertResult1 && assertResult2) || (assertResult1 && assertResult3))
    }

  def andIsAssociative: Future[Boolean] =
    forall {
      val assertResult1 = randomAssertResult()
      val assertResult2 = randomAssertResult()
      val assertResult3 = randomAssertResult()
      ((assertResult1 && assertResult2) && assertResult3) == (assertResult1 && (assertResult2 && assertResult3))
    }

  def andIsCommutative: Future[Boolean] =
    forall {
      val assertResult1 = randomAssertResult()
      val assertResult2 = randomAssertResult()
      (assertResult1 && assertResult2) == (assertResult2 && assertResult1)
    }

  def anyReturnsDisjunctionOfAssertResults: Future[Boolean] =
    Future.successful(AssertResult.any(List(success1, failure1, failure2)).get.isSuccess)

  def asMapsValuesToConstantValue: Future[Boolean] =
    Future.successful {
      (value1 && value2).as("failure") ==
        (AssertResult.value("failure") && AssertResult.value("failure"))
    }

  def bothReturnsConjunctionOfTwoAssertResults: Future[Boolean] =
    Future.successful {
      (success1 && success2).isSuccess &&
      (success1 && failure1).isFailure &&
      (failure1 && success1).isFailure &&
      (failure1 && failure2).isFailure
    }

  def collectAllCombinesMultipleAssertResults: Future[Boolean] =
    Future.successful {
      AssertResult.collectAll(List(success1, failure1, failure2)) ==
        Some((success1 && failure1 && failure2))
    }

  def eitherReturnsDisjunctionOfTwoAssertResults: Future[Boolean] =
    Future.successful {
      (success1 || success2).isSuccess &&
      (success1 || failure1).isSuccess &&
      (failure1 || success1).isSuccess &&
      (failure1 || failure2).isFailure
    }

  def failuresCollectsFailedAssertResults: Future[Boolean] =
    Future.successful {
      val actual   = (success1 && success2 && failure1 && failure2).failures.get
      val expected = value1 && value2
      actual == expected
    }

  def foreachCombinesMultipleassertResults: Future[Boolean] =
    Future.successful {
      def isEven(n: Int): AssertResult[String] =
        if (n % 2 == 0) AssertResult.value(s"$n is even")
        else AssertResult.value(s"$n is odd")
      AssertResult.foreach(List(1, 2, 3))(isEven) ==
        Some {
          AssertResult.value("1 is odd") &&
          AssertResult.value("2 is even") &&
          AssertResult.value("3 is odd")
        }
    }

  def hashCodeIsConsistentWithEquals: Future[Boolean] =
    Future.successful {
      (1 to 10).forall { _ =>
        val (assertResult1, assertResult2) = randomEqualassertResults(4)
        assertResult1.hashCode == assertResult2.hashCode
      }
    }

  def isFailureReturnsWhetherAssertResultFailed: Future[Boolean] =
    Future.successful(!success1.isFailure && failure1.isFailure)

  def isSuccessReturnsWhetherAssertResultSucceeded: Future[Boolean] =
    Future.successful(success1.isSuccess && !failure1.isSuccess)

  def mapTransformsValues: Future[Boolean] =
    Future.successful {
      val actual = (success1 && failure1 && failure2).map {
        case Left(s)  => Left(s.split(" ").head)
        case Right(s) => Right(s.split(" ").head)
      }
      val expected = (AssertResult.success("first") && AssertResult.failure("first") && AssertResult.failure("second"))
      actual == expected
    }

  def notNegatesAssertResults: Future[Boolean] =
    Future.successful {
      (success1.not == AssertResult.failure(message1)) &&
      (failure1.not == AssertResult.success(message3))
    }

  def orDistributesOverAnd: Future[Boolean] =
    forall {
      val assertResult1 = randomAssertResult()
      val assertResult2 = randomAssertResult()
      val assertResult3 = randomAssertResult()
      (assertResult1 || (assertResult2 && assertResult3)) ==
        ((assertResult1 || assertResult2) && (assertResult1 || assertResult3))
    }

  def orIsAssociative: Future[Boolean] =
    forall {
      val assertResult1 = randomAssertResult()
      val assertResult2 = randomAssertResult()
      val assertResult3 = randomAssertResult()
      ((assertResult1 || assertResult2) || assertResult3) == (assertResult1 || (assertResult2 || assertResult3))
    }

  def orIsCommutative: Future[Boolean] =
    forall {
      val assertResult1 = randomAssertResult()
      val assertResult2 = randomAssertResult()
      (assertResult1 || assertResult2) == (assertResult2 || assertResult1)
    }

  def forall(p: => Boolean): Future[Boolean] =
    Future.successful((1 to 100).forall(_ => p))

  def randomAssertResult(size: Int = Random.nextInt(10)): AssertResult[Int] =
    if (size == 0) {
      AssertResult.Value(Random.nextInt(10))
    } else {
      val n = Random.nextInt(size)
      Random.nextInt(2) match {
        case 0 => AssertResult.And(randomAssertResult(n), randomAssertResult(size - n - 1))
        case 1 => AssertResult.Or(randomAssertResult(n), randomAssertResult(size - n - 1))
      }
    }

  def randomEqualassertResults(size: Int = 3): (AssertResult[Int], AssertResult[Int]) = {
    val assertResult1 = randomAssertResult(size)
    val assertResult2 = randomAssertResult(size)
    if (assertResult1 == assertResult2) (assertResult1, assertResult2) else randomEqualassertResults(size)
  }
}
