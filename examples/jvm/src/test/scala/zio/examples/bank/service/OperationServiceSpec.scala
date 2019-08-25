package zio.examples.bank.service

import zio.ZIO
import zio.examples.bank.TestEnvironment.testEnv
import zio.examples.bank.domain.{ CreateAccount, CreateOperation, CreateTransaction, Credit }
import zio.examples.bank.environment.Environments.BankEnvironment
import zio.examples.bank.failure.OperationFailure
import zio.examples.bank.service.AccountServiceImpl.createAccount
import zio.examples.bank.service.OperationServiceImpl._
import zio.test.Predicate.{ equalTo, isRight }
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, TestResult }

object OperationServiceSpec
    extends DefaultRunnableSpec(
      suite("OperationSpec")(
        testM("Deposit US$ 1") {

          val createAccountCommand = CreateAccount("John Doe")

          val pipeline = for {
            account       <- createAccount(createAccountCommand).orDieWith(_ => new Exception("Account Failure"))
            createDeposit = CreateOperation(100, account.id, 1, List(CreateTransaction(account, 100, Credit)), true)
            _             <- createOperation(createDeposit)
            balance       <- findBalance(account.id)
          } yield balance.valueInCents

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Long]](pipeline.either, isRight(equalTo(100L)))

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
      )
    )
