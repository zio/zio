package zio.examples.bank.service

import zio.ZIO
import zio.examples.bank.TestEnvironment.testEnv
import zio.examples.bank.domain._
import zio.examples.bank.environment.Environments.BankEnvironment
import zio.examples.bank.failure.OperationFailure
import zio.examples.bank.service.AccountServiceImpl.createAccount
import zio.examples.bank.service.OperationServiceImpl._
import zio.test.Predicate.{ equalTo, isRight }
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, TestResult }

object OperationServiceSpec
    extends DefaultRunnableSpec(
      suite("OperationSpec")(
        testM("Deposit US$ 5.00") {

          val createAccountCommand = CreateAccount("John Doe")

          val pipeline = for {
            account       <- createAccount(createAccountCommand).orDieWith(_ => new Exception("Account Failure"))
            createDeposit = CreateOperation(500, account.id, 1, List(CreateTransaction(account, 500, Credit)), true)
            _             <- createOperation(createDeposit)
            balance       <- findBalance(account.id)
          } yield balance.valueInCents

          val assertion =
            assertM[BankEnvironment, Either[OperationFailure, Long]](pipeline.either, isRight(equalTo(500L)))

          (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

        },
        testM("Deposit US$ 5.00 and Transfer US$ 4.00") {

          val createAccountCommand  = CreateAccount("John Doe")
          val createAccountCommand2 = CreateAccount("Anna P. Erwin")

          val pipeline = for {
            account     <- createAccount(createAccountCommand).orDieWith(_ => new Exception("Account Failure"))
            peerAccount <- createAccount(createAccountCommand2).orDieWith(_ => new Exception("Account Failure"))
            createDeposit = CreateOperation(500,
                                            account.id,
                                            1,
                                            List(CreateTransaction(account, 500, Credit)),
                                            isExternal = true)
            _ <- createOperation(createDeposit)
            createTransfer = CreateOperation(400,
                                             account.id,
                                             1,
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
