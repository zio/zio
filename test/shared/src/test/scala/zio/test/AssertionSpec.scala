package zio.test

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

import zio.DefaultRuntime
import zio.test.TestUtils.label

object AssertResultSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    // label(allSucceedsIfAllAssertionsSucceed, "all succeds if all assertions succeed"),
    label(andDistributesOverOr, "and distributes over or"),
    label(andIsAssociative, "and is associative"),
    label(andIsCommutative, "and is commutative"),
    // label(anySucceedsIfAnyAssertionsSucceed, "any succeeds if any assertion succeeds"),
    // label(asMapsFailureMessagestoConstantValue, "as maps failure messages to constant value"),
    label(bothReturnsConjunctionOfTwoAssertions, "both returns conjunction of two assertions"),
    // label(collectAllCombinesMultipleAssertions, "collectAll combines multiple assertions"),
    label(eitherReturnsDisjunctionOfTwoAssertions, "either returns disjunction of two assertions"),
    // label(failuresReturnsListOfFailures, "failures returns list of failures"),
    // label(hashCodeIsConsistentWithEquals, "hashCode is consistent with equals"),
    // label(foreachCombinesMultipleAssertions, "foreach combines multiple assertions"),
    label(isFailureReturnsWhetherAssertionFailed, "isFailure returns whether assertion failed"),
    label(isSuccessReturnsWhetherAssertionSucceeded, "isSuccess returns whether assertion succeeded"),
    // label(mapTransformsFailureMessages, "map transforms failure messages"),
    // label(notNegatesAssertions, "not negates assertions"),
    label(orDistributesOverAnd, "or distributes over and"),
    label(orIsAssociative, "or is associative"),
    label(orIsCommutative, "or is commutative")
  )
  val message1 = "first success"
  val message2 = "second success"
  val message3 = "first failure"
  val message4 = "second failure"

  val success1 = AssertResult.value(Right(message1))
  val success2 = AssertResult.value(Right(message2))
  val failure1 = AssertResult.value(Left(message3))
  val failure2 = AssertResult.value(Left(message4))

  def allSucceedsIfAllAssertionsSucceed: Future[Boolean] =
    Future.successful(AssertResult.all(List(success1, failure1, failure2)).isFailure)

  def andDistributesOverOr: Future[Boolean] =
    forall {
      val assertion1 = randomAssertion()
      val assertion2 = randomAssertion()
      val assertion3 = randomAssertion()
      (assertion1 && (assertion2 || assertion3)) ==
        ((assertion1 && assertion2) || (assertion1 && assertion3))
    }

  def andIsAssociative: Future[Boolean] =
    forall {
      val assertion1 = randomAssertion()
      val assertion2 = randomAssertion()
      val assertion3 = randomAssertion()
      ((assertion1 && assertion2) && assertion3) == (assertion1 && (assertion2 && assertion3))
    }

  def andIsCommutative: Future[Boolean] =
    forall {
      val assertion1 = randomAssertion()
      val assertion2 = randomAssertion()
      (assertion1 && assertion2) == (assertion2 && assertion1)
    }

  def anySucceedsIfAnyAssertionsSucceed: Future[Boolean] =
    Future.successful(AssertResult.any(List(success1, failure1, failure2)).isSuccess)

  // def asMapsFailureMessagestoConstantValue: Future[Boolean] =
  //   Future.successful {
  //     (failure1 && failure2).as("failure") ==
  //       (Assertion.failure("failure") && Assertion.failure("failure"))
  //   }

  def bothReturnsConjunctionOfTwoAssertions: Future[Boolean] =
    Future.successful {
      (success1 && success2).isSuccess &&
      (success1 && failure1).isFailure &&
      (failure1 && success1).isFailure &&
      (failure1 && failure2).isFailure
    }

  def collectAllCombinesMultipleAssertions: Future[Boolean] =
    Future.successful {
      AssertResult.collectAll(List(success1, failure1, failure2)) ==
        (success1 && failure1 && failure2)
    }

  def eitherReturnsDisjunctionOfTwoAssertions: Future[Boolean] =
    Future.successful {
      (success1 || success2).isSuccess &&
      (success1 || failure1).isSuccess &&
      (failure1 || success1).isSuccess &&
      (failure1 || failure2).isFailure
    }

  // def failuresReturnsListOfFailures: Future[Boolean] =
  //   Future.successful((failure1 && failure2).failures == List(message3, message4))

  def foreachCombinesMultipleAssertions: Future[Boolean] =
    Future.successful {
      def isEven(n: Int): AssertResult[String] =
        if (n % 2 == 0) AssertResult.value(s"$n is not even")
        else AssertResult.value(s"$n is oodd")
      AssertResult.foreach(List(1, 2, 3))(isEven) ==
        (AssertResult.value("1 is odd") &&
        AssertResult.value("2 is even") &&
        AssertResult.value("3 is odd"))
    }

  def hashCodeIsConsistentWithEquals: Future[Boolean] =
    Future.successful {
      (1 to 10).forall { _ =>
        val (assertion1, assertion2) = randomEqualAssertions(4)
        assertion1.hashCode == assertion2.hashCode
      }
    }

  def isFailureReturnsWhetherAssertionFailed: Future[Boolean] =
    Future.successful(!success1.isFailure && failure1.isFailure)

  def isSuccessReturnsWhetherAssertionSucceeded: Future[Boolean] =
    Future.successful(success1.isSuccess && !failure1.isSuccess)

  // def mapTransformsFailureMessages: Future[Boolean] =
  //   Future.successful {
  //     (success1 && failure1 && failure2).map(_.split(" ").head) ==
  //       (success1 && AssertResult.failure("first") && AssertResult.failure("second"))
  //   }

  // def notNegatesAssertions: Future[Boolean] =
  //   Future.successful {
  //     (success1.not == AssertResult.failure(message1)) &&
  //     (failure1.not == AssertResult.success(message3))
  //   }

  def orDistributesOverAnd: Future[Boolean] =
    forall {
      val assertion1 = randomAssertion()
      val assertion2 = randomAssertion()
      val assertion3 = randomAssertion()
      (assertion1 || (assertion2 && assertion3)) ==
        ((assertion1 || assertion2) && (assertion1 || assertion3))
    }

  def orIsAssociative: Future[Boolean] =
    forall {
      val assertion1 = randomAssertion()
      val assertion2 = randomAssertion()
      val assertion3 = randomAssertion()
      ((assertion1 || assertion2) || assertion3) == (assertion1 || (assertion2 || assertion3))
    }

  def orIsCommutative: Future[Boolean] =
    forall {
      val assertion1 = randomAssertion()
      val assertion2 = randomAssertion()
      (assertion1 || assertion2) == (assertion2 || assertion1)
    }

  def forall(p: => Boolean): Future[Boolean] =
    Future.successful((1 to 100).forall(_ => p))

  def randomAssertion(size: Int = Random.nextInt(10)): AssertResult[Int] =
    if (size == 0) {
      AssertResult.Value(Random.nextInt(10))
    } else {
      val n = Random.nextInt(size)
      Random.nextInt(2) match {
        case 0 => AssertResult.And(randomAssertion(n), randomAssertion(size - n - 1))
        case 1 => AssertResult.Or(randomAssertion(n), randomAssertion(size - n - 1))
      }
    }

  def randomEqualAssertions(size: Int = 3): (AssertResult[Int], AssertResult[Int]) = {
    val assertion1 = randomAssertion(size)
    val assertion2 = randomAssertion(size)
    if (assertion1 == assertion2) (assertion1, assertion2) else randomEqualAssertions(size)
  }
}
