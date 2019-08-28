package zio.examples.bank.service

import zio.ZIO
import zio.examples.bank.TestEnvironment.testEnv
import zio.examples.bank.domain._
import zio.examples.bank.environment.Environments.{ AccountEnvironment, BankEnvironment, OperationEnvironment }
import zio.examples.bank.failure.OperationFailure
import zio.examples.bank.service.OperationServiceImpl._
import zio.test.Predicate.{ equalTo, isRight }
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, TestResult }
import OperationServiceTests._

private object OperationServiceTests {

  def createAccount(ownerName: String): ZIO[AccountEnvironment, Nothing, Account] =
    AccountServiceImpl.createAccount(CreateAccount(ownerName)).orDieWith(_ => new Exception("Account Failure"))

  def createAccountWithValue(ownerName: String, value: Long): ZIO[OperationEnvironment, OperationFailure, Account] =
    for {
      account       <- createAccount(ownerName)
      createDeposit = CreateOperation(value, account.id, 1, List(CreateTransaction(account, value, Credit)), true)
      _             <- createOperation(createDeposit)
    } yield account

}

object OperationServiceSpec
    extends DefaultRunnableSpec(
      suite("OperationSpec")(
        testM("Deposit US$ 5.00") {

          val pipeline = for {
            account <- createAccountWithValue("John Doe", 500)
            balance <- findBalance(account.id)
          } yield balance.valueInCents

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Long]](pipeline.either, isRight(equalTo(500L)))

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Deposit US$ 5.00 and Transfer US$ 4.00") {

          val pipeline = for {
            account     <- createAccountWithValue("John Doe", 500L)
            peerAccount <- createAccount("Anna P. Erwin")
            createTransfer = CreateOperation(400,
                                             account.id,
                                             peerAccount.id,
                                             List(CreateTransaction(account, 400, Debit),
                                                  CreateTransaction(peerAccount, 400, Credit)),
                                             isExternal = false)
            _            <- createOperation(createTransfer)
            ownerBalance <- findBalance(account.id)
            peerBalance  <- findBalance(peerAccount.id)
          } yield (ownerBalance.valueInCents, peerBalance.valueInCents)

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, (Long, Long)]](pipeline.either,
                                                                             isRight(equalTo((100L, 400L))))

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
      )
    )
