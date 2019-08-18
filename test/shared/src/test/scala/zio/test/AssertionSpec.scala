package zio.test

import scala.concurrent.{ ExecutionContext, Future }

import zio.DefaultRuntime
import zio.test.TestUtils.label

object AssertionSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
    label(bothChoosesTheFirstFailure, "both chooses the first failure"),
    label(bothSymbolicOperatorWorksCorrectly, "both symbolic operator works correctly"),
    label(bothWithCombinesFailureMessages, "bothWith combines failure messages"),
    label(collectAllCombinesMultipleAssertions, "collectAll combines multiple asserts"),
    label(eitherChoosesTheFirstFailure, "either chooses the first failure"),
    label(eitherWithCombinesFailureMessages, "eitherWith combines failure messages"),
    label(eitherSymbolicOperatorWorksCorrectly, "either symbolic operator works correctly"),
    label(foreachCombinesMultipleAssertions, "foreach combines multiple assertions")
  )

  val message1 = "first failure"
  val message2 = "second failure"
  val message3 = "first failure and second failure"

  val success  = Assertion.success
  val failure1 = Assertion.failure(message1)
  val failure2 = Assertion.failure(message2)
  val failure3 = Assertion.failure(message3)

  def bothChoosesTheFirstFailure: Future[Boolean] =
    Future.successful(failure1.both(failure2) == failure1)

  def bothSymbolicOperatorWorksCorrectly: Future[Boolean] =
    Future.successful {
      ((success && success) == success) &&
      ((success && failure1) == failure1) &&
      ((failure1 && success) == failure1) &&
      ((failure1 && failure2) == failure1)
    }

  def bothWithCombinesFailureMessages: Future[Boolean] =
    Future.successful(failure1.bothWith(failure2)(_ + " and " + _) == failure3)

  def collectAllCombinesMultipleAssertions: Future[Boolean] =
    Future.successful {
      Assertion.collectAll(List(success, failure1, failure2)) ==
        Assertion.failure(List(message1, message2))
    }

  def eitherChoosesTheFirstFailure: Future[Boolean] =
    Future.successful(failure1.either(failure2) == failure1)

  def eitherSymbolicOperatorWorksCorrectly: Future[Boolean] =
    Future.successful {
      ((success || success) == success) &&
      ((success || failure1) == success) &&
      ((failure1 || success) == success) &&
      ((failure1 || failure2) == failure1)
    }

  def eitherWithCombinesFailureMessages: Future[Boolean] =
    Future.successful(failure1.eitherWith(failure2)(_ + " and " + _) == failure3)

  def foreachCombinesMultipleAssertions: Future[Boolean] =
    Future.successful {
      def isEven(n: Int): Assertion[String] =
        if (n % 2 == 0) Assertion.success else Assertion.failure(s"$n is not even")
      Assertion.foreach(List(1, 2, 3))(isEven) ==
        Assertion.failure(List("1 is not even", "3 is not even"))
    }
}
