package zio.test

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

import zio.DefaultRuntime
import zio.test.TestUtils.label

object AssertionSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(allSucceedsIfAllAssertionsSucceed, "all succeds if all assertions succeed"),
    label(andDistributesOverOr, "and distributes over or"),
    label(andIsAssociative, "and is associative"),
    label(andIsCommutative, "and is commutative"),
    label(anySucceedsIfAnyAssertionsSucceed, "any succeeds if any assertion succeeds"),
    label(asMapsFailureMessagestoConstantValue, "as maps failure messages to constant value"),
    label(bothReturnsConjunctionOfTwoAssertions, "both returns conjunction of two assertions"),
    label(collectAllCombinesMultipleAssertions, "collectAll combines multiple assertions"),
    label(eitherReturnsDisjunctionOfTwoAssertions, "either returns disjunction of two assertions"),
    label(failuresReturnsListOfFailures, "failures returns list of failures"),
    label(hashCodeIsConsistentWithEquals, "hashCode is consistent with equals"),
    label(foreachCombinesMultipleAssertions, "foreach combines multiple assertions"),
    label(ignoreIsEmptyElementForAnd, "ignore is empty element for and"),
    label(ignoreIsEmptyElementForOr, "ignore is empty element for or"),
    label(isFailureReturnsWhetherAssertionFailed, "isFailure returns whether assertion failed"),
    label(isSuccessReturnsWhetherAssertionSucceeded, "isSuccess returns whether assertion succeeded"),
    label(mapTransformsFailureMessages, "map transforms failure messages"),
    label(notNegatesAssertions, "not negates assertions"),
    label(orDistributesOverAnd, "or distributes over and"),
    label(orIsAssociative, "or is associative"),
    label(orIsCommutative, "or is commutative")
  )

  val message1 = "first failure"
  val message2 = "second failure"

  val ignore   = Assertion.ignore
  val success  = Assertion.success
  val failure1 = Assertion.failure(message1)
  val failure2 = Assertion.failure(message2)

  def allSucceedsIfAllAssertionsSucceed: Future[Boolean] =
    Future.successful(Assertion.all(List(success, failure1, failure2)).isFailure)

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
    Future.successful(Assertion.any(List(success, failure1, failure2)).isSuccess)

  def asMapsFailureMessagestoConstantValue: Future[Boolean] =
    Future.successful {
      (failure1 && failure2).as("failure") ==
        (Assertion.failure("failure") && Assertion.failure("failure"))
    }

  def bothReturnsConjunctionOfTwoAssertions: Future[Boolean] =
    Future.successful {
      (success && success).isSuccess &&
      (success && failure1).isFailure &&
      (failure1 && success).isFailure &&
      (failure1 && failure2).isFailure
    }

  def collectAllCombinesMultipleAssertions: Future[Boolean] =
    Future.successful {
      Assertion.collectAll(List(success, failure1, failure2)) ==
        (success && failure1 && failure2)
    }

  def eitherReturnsDisjunctionOfTwoAssertions: Future[Boolean] =
    Future.successful {
      (success || success).isSuccess &&
      (success || failure1).isSuccess &&
      (failure1 || success).isSuccess &&
      (failure1 || failure2).isFailure
    }

  def failuresReturnsListOfFailures: Future[Boolean] =
    Future.successful((failure1 && failure2).failures == List(message1, message2))

  def foreachCombinesMultipleAssertions: Future[Boolean] =
    Future.successful {
      def isEven(n: Int): Assertion[String] =
        if (n % 2 == 0) Assertion.success else Assertion.failure(s"$n is not even")
      Assertion.foreach(List(1, 2, 3))(isEven) ==
        (Assertion.failure("1 is not even") &&
        Assertion.success &&
        Assertion.failure("3 is not even"))
    }

  def hashCodeIsConsistentWithEquals: Future[Boolean] =
    Future.successful {
      (1 to 10).forall { _ =>
        val (assertion1, assertion2) = randomEqualAssertions(4)
        assertion1.hashCode == assertion2.hashCode
      }
    }

  def ignoreIsEmptyElementForAnd: Future[Boolean] =
    forall {
      val assertion = randomAssertion()
      ((assertion && Assertion.ignore) == assertion) &&
      (Assertion.ignore && assertion) == assertion
    }

  def ignoreIsEmptyElementForOr: Future[Boolean] =
    forall {
      val assertion = randomAssertion()
      ((assertion || Assertion.ignore) == assertion) &&
      (Assertion.ignore || assertion) == assertion
    }

  def isFailureReturnsWhetherAssertionFailed: Future[Boolean] =
    Future.successful(!ignore.isFailure && !success.isFailure && failure1.isFailure)

  def isSuccessReturnsWhetherAssertionSucceeded: Future[Boolean] =
    Future.successful(!ignore.isSuccess && success.isSuccess && !failure1.isSuccess)

  def mapTransformsFailureMessages: Future[Boolean] =
    Future.successful {
      (success && failure1 && failure2).map(_.split(" ").head) ==
        (success && Assertion.failure("first") && Assertion.failure("second"))
    }

  def notNegatesAssertions: Future[Boolean] =
    Future.successful {
      (ignore.not("not") == ignore) &&
      (success.not("not") == Assertion.failure("not")) &&
      (failure1.not("not") == success)
    }

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

  def randomAssertion(size: Int = Random.nextInt(10)): Assertion[Int] =
    if (size == 0) {
      Random.nextInt(3) match {
        case 0 => Assertion.Ignore
        case 1 => Assertion.Success
        case 2 => Assertion.Failure(Random.nextInt(10))
      }
    } else {
      val n = Random.nextInt(size)
      Random.nextInt(2) match {
        case 0 => Assertion.And(randomAssertion(n), randomAssertion(size - n - 1))
        case 1 => Assertion.Or(randomAssertion(n), randomAssertion(size - n - 1))
      }
    }

  def randomEqualAssertions(size: Int = 3): (Assertion[Int], Assertion[Int]) = {
    val assertion1 = randomAssertion(size)
    val assertion2 = randomAssertion(size)
    if (assertion1 == assertion2) (assertion1, assertion2) else randomEqualAssertions(size)
  }
}
