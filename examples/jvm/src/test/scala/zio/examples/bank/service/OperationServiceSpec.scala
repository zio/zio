package zio.examples.bank.service

import java.time.LocalDate

import zio.ZIO
import zio.clock.Clock
import zio.duration._
import zio.examples.bank.TestEnvironment.testEnv
import zio.examples.bank.domain._
import zio.examples.bank.effect.{ AccountRepository, Logger, OperationRepository }
import zio.examples.bank.environment.Environments.{ AccountEnvironment, BankEnvironment }
import zio.examples.bank.failure._
import zio.examples.bank.service.OperationServiceImpl._
import zio.examples.bank.service.OperationServiceTests._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

private object OperationServiceTests {

  // Allows to compose account with operation eliminating Account failures
  def createAccount(ownerName: String): ZIO[AccountEnvironment, Nothing, Account] =
    AccountServiceImpl.createAccount(CreateAccount(ownerName)).orDieWith(_ => new Exception("Account Failure"))

  def createAccountWithValue(ownerName: String, value: Long): ZIO[BankEnvironment, OperationFailure, Account] =
    ZIO.accessM { env =>
      for {
        account <- createAccount(ownerName)
        today   <- env.clock.currentDateTime.map(_.toLocalDate)
        createDeposit = CreateOperation(
          value,
          account.id,
          account.id,
          List(CreateTransaction(account, value, Credit, today)),
          isExternal = true
        )
        _ <- createOperation(createDeposit)
      } yield account

    }

  def createAccountWithValueScheduled(ownerName: String,
                                      value: Long,
                                      date: LocalDate): ZIO[BankEnvironment, OperationFailure, Account] =
    for {
      account <- createAccount(ownerName)
      createDeposit = CreateOperation(
        value,
        account.id,
        account.id,
        List(CreateTransaction(account, value, Credit, date)),
        isExternal = true
      )
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
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Long]](pipeline.either, isRight(equalTo(500L)))

          testEnv >>= assertion.provide

        },
        testM("Deposit US$ 5.00 and Transfer US$ 4.00") {

          val pipeline = for {
            account     <- createAccountWithValue("John Doe", 500L)
            peerAccount <- createAccount("Anna P. Erwin")
            today       <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate))
            createTransfer = CreateOperation(
              400L,
              account.id,
              peerAccount.id,
              List(CreateTransaction(account, 400L, Debit, today), CreateTransaction(peerAccount, 400L, Credit, today)),
              isExternal = false
            )
            _            <- createOperation(createTransfer)
            ownerBalance <- findBalance(account.id)
            peerBalance  <- findBalance(peerAccount.id)
          } yield (ownerBalance.valueInCents, peerBalance.valueInCents)

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, (Long, Long)]](
              pipeline.either,
              isRight(equalTo((100L, 400L)))
            )

          testEnv >>= assertion.provide

        },
        testM("Cannot create an operation without transactions") {

          val pipeline = for {
            account       <- createAccount("John Doe")
            createDeposit = CreateOperation(100L, account.id, account.id, Nil, isExternal = true)
            _             <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationWithoutTransactions()))
            )

          testEnv >>= assertion.provide

        },
        testM("Cannot create an operation with an invalid value (value < 1)") {

          val pipeline = for {
            account <- createAccount("John Doe")
            today   <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate))
            createDeposit = CreateOperation(
              0L,
              account.id,
              account.id,
              List(CreateTransaction(account, 0L, Debit, today)),
              isExternal = true
            )
            _ <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationInvalidValue(0L)))
            )

          testEnv >>= assertion.provide

        },
        testM("Cannot create an operation without a different sum of transactions value") {

          val pipeline = for {
            account <- createAccount("John Doe")
            today   <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate))
            createDeposit = CreateOperation(
              100L,
              account.id,
              account.id,
              List(CreateTransaction(account, 50L, Debit, today)),
              isExternal = true
            )
            _ <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationValueAndSumOfTransactionsDifferent(100L, 50L)))
            )

          testEnv >>= assertion.provide

        },
        testM("Cannot create an operation without an existent account") {

          val pipeline = for {
            today <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate))
            createDeposit = CreateOperation(
              100L,
              0,
              0,
              List(CreateTransaction(Account(0, "Nonexistent account"), 100L, Credit, today)),
              isExternal = true
            )
            _ <- createOperation(createDeposit)
          } yield ()

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationNotFoundAccount(0, Some("owner"))))
            )

          testEnv >>= assertion.provide

        },
        testM("Cannot create an operation with an invalid transaction") {

          val pipeline = for {
            account            <- createAccount("John Doe")
            today              <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate))
            invalidTransaction = CreateTransaction(account, 0L, Debit, today)
            createDeposit = CreateOperation(
              100L,
              account.id,
              account.id,
              List(CreateTransaction(account, 100L, Debit, today), invalidTransaction),
              isExternal = true
            )
            failure <- createOperation(createDeposit).either
            result <- failure match {
                       case Left(OperationWithInvalidCreateTransactions(l)) =>
                         ZIO.succeed(l == List(invalidTransaction))
                       case _ => ZIO.succeed(false)
                     }
          } yield result

          val assertion =
            assertM[BankEnvironment, Nothing, Boolean](pipeline, isTrue)

          testEnv >>= assertion.provide

        },
        testM("Cannot transfer more money than the current balance") {

          val pipeline = for {
            account     <- createAccountWithValue("John Doe", 500L)
            peerAccount <- createAccount("Anna P. Erwin")
            today       <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate))
            createTransfer = CreateOperation(
              600L,
              account.id,
              peerAccount.id,
              List(CreateTransaction(account, 600L, Debit, today), CreateTransaction(peerAccount, 600L, Credit, today)),
              isExternal = false
            )
            _ <- createOperation(createTransfer)

          } yield ()
          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Unit]](
              pipeline.either,
              isLeft(equalTo(OperationOwnerAccountInsufficientValue(600L, 500L)))
            )

          testEnv >>= assertion.provide

        },
        testM("Balance don't aggregates an US$ 5.00 deposit scheduled") {

          val pipeline = for {
            tomorrow <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate.plusDays(1L)))
            account  <- createAccountWithValueScheduled("John Doe", 500L, tomorrow)
            balance  <- findBalance(account.id)
          } yield balance.valueInCents

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, Long]](pipeline.either, isRight(equalTo(0L)))

          testEnv >>= assertion.provide

        },
        testM("Balance aggregates an US$ 5.00 deposit scheduled when time passes") {

          val pipeline: ZIO[BankEnvironment, OperationFailure, (Long, Long)] = for {
            tomorrow     <- ZIO.accessM[Clock](_.clock.currentDateTime.map(_.toLocalDate.plusDays(1L)))
            account      <- createAccountWithValueScheduled("John Doe", 500L, tomorrow)
            todayBalance <- findBalance(account.id)
            newTime      <- TestClock.makeTest(TestClock.DefaultData.copy())
            _            <- newTime.adjust((24 * 60 * 60).seconds)
            newEnv <- ZIO
                       .environment[BankEnvironment]
                       .map(
                         ce =>
                           new Clock with Logger with AccountRepository with OperationRepository {
                             override val log: Logger.Effect                              = ce.log
                             override val accountRepository: AccountRepository.Effect     = ce.accountRepository
                             override val clock: Clock.Service[Any]                       = newTime
                             override val operationRepository: OperationRepository.Effect = ce.operationRepository
                         }
                       )
            tomorrowBalance <- findBalance(account.id).provide(newEnv)

          } yield (todayBalance.valueInCents, tomorrowBalance.valueInCents)

          val customAssertion = assertionDirect[(Long, Long)]("")()(
            balances => equalTo(0L)(balances._1) && equalTo(500L)(balances._2)
          )

          val assertion =
            assertM[BankEnvironment, Nothing, Either[OperationFailure, (Long, Long)]](pipeline.either,
                                                                                      isRight(customAssertion))

          testEnv >>= assertion.provide

        },
      )
    )
