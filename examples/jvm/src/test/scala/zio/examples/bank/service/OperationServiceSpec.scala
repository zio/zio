package zio.examples.bank.service

import zio.ZIO
import zio.examples.bank.TestEnvironment.testEnv
import zio.examples.bank.domain._
import zio.examples.bank.environment.Environments.{ AccountEnvironment, BankEnvironment, OperationEnvironment }
import zio.examples.bank.failure.{
  OperationFailure,
  OperationInvalidValue,
  OperationNotFoundAccount,
  OperationOwnerAccountInsufficientValue,
  OperationValueAndSumOfTransactionsDifferent,
  OperationWithInvalidCreateTransactions,
  OperationWithoutTransactions
}
import zio.examples.bank.service.OperationServiceImpl._
import zio.examples.bank.service.OperationServiceTests._
import zio.test.Predicate._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, TestResult }

private object OperationServiceTests {

  // Allows to compose account with operation eliminating Account failures
  def createAccount(ownerName: String): ZIO[AccountEnvironment, Nothing, Account] =
    AccountServiceImpl.createAccount(CreateAccount(ownerName)).orDieWith(_ => new Exception("Account Failure"))

  def createAccountWithValue(ownerName: String, value: Long): ZIO[OperationEnvironment, OperationFailure, Account] =
    for {
      account <- createAccount(ownerName)
      createDeposit = CreateOperation(value,
                                      account.id,
                                      account.id,
                                      List(CreateTransaction(account, value, Credit)),
                                      isExternal = true)
      _ <- createOperation(createDeposit)
    } yield account

}

object OperationServiceSpec
    extends DefaultRunnableSpec(
      suite("OperationServiceSpec")(
        testM("Deposit US$ 5.00") {

          val pipeline = for {
            account <- createAccountWithValue("John Doe", 500L)
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
            createTransfer = CreateOperation(400L,
                                             account.id,
                                             peerAccount.id,
                                             List(CreateTransaction(account, 400L, Debit),
                                                  CreateTransaction(peerAccount, 400L, Credit)),
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
        testM("Cannot create an operation without transactions") {

          val pipeline = for {
            account       <- createAccount("John Doe")
            createDeposit = CreateOperation(100L, account.id, account.id, Nil, isExternal = true)
            _             <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationWithoutTransactions()))
            )

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Cannot create an operation without an invalid value (value < 1)") {

          val pipeline = for {
            account <- createAccount("John Doe")
            createDeposit = CreateOperation(0L,
                                            account.id,
                                            account.id,
                                            List(CreateTransaction(account, 0L, Debit)),
                                            isExternal = true)
            _ <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationInvalidValue(0L)))
            )

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Cannot create an operation without an different sum of transactions value") {

          val pipeline = for {
            account <- createAccount("John Doe")
            createDeposit = CreateOperation(100L,
                                            account.id,
                                            account.id,
                                            List(CreateTransaction(account, 50L, Debit)),
                                            isExternal = true)
            _ <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationValueAndSumOfTransactionsDifferent(100L, 50L)))
            )

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Cannot create an operation without an existent account") {

          val createDeposit =
            CreateOperation(100L,
                            0,
                            0,
                            List(CreateTransaction(Account(0, "Nonexistent account"), 100L, Credit)),
                            isExternal = true)

          val pipeline = for {
            _ <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationNotFoundAccount(0, Some("owner"))))
            )

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Cannot create an operation with an invlid transaction") {

          val pipeline = for {
            account            <- createAccount("John Doe")
            invalidTransaction = CreateTransaction(account, 0L, Debit)
            createDeposit = CreateOperation(100L,
                                            account.id,
                                            account.id,
                                            List(CreateTransaction(account, 100L, Debit), invalidTransaction),
                                            isExternal = true)
            failure <- createOperation(createDeposit).either
            result <- failure match {
                       case Left(OperationWithInvalidCreateTransactions(l)) =>
                         ZIO.succeed(l == List(invalidTransaction))
                       case _ => ZIO.succeed(false)
                     }
          } yield result

          val assertion =
            assertM[BankEnvironment, Boolean](pipeline, isTrue)

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Cannot transfer more money than current balance") {

          val pipeline = for {
            account     <- createAccountWithValue("John Doe", 500L)
            peerAccount <- createAccount("Anna P. Erwin")
            createTransfer = CreateOperation(600L,
                                             account.id,
                                             peerAccount.id,
                                             List(CreateTransaction(account, 600L, Debit),
                                                  CreateTransaction(peerAccount, 600L, Credit)),
                                             isExternal = false)
            _ <- createOperation(createTransfer)

          } yield ()
          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationOwnerAccountInsufficientValue(600L, 500L)))
            )

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
      )
    )
