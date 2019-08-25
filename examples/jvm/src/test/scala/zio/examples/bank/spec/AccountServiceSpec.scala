package zio.examples.bank.spec

import zio.ZIO
import zio.examples.bank.TestEnvironment._
import zio.examples.bank.domain.{ Account, CreateAccount }
import zio.examples.bank.environment.Environments.BankEnvironment
import zio.examples.bank.failure.{ AccountFailure, AccountNotFound }
import zio.examples.bank.service.AccountServiceImpl._
import zio.test.Predicate._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, TestResult }
object AccountServiceSpec
    extends DefaultRunnableSpec(
      {
        suite("AccountSpec")(
          testM("Create account") {

            val command = CreateAccount("John Doe")

            val pipeline = createAccount(command).either

            val assertion =
              assertM[BankEnvironment, Either[AccountFailure, Account]](
                pipeline,
                isRight(equalTo(Account(1, "John Doe")))
              )

            (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

          },
          testM("Find an existent account") {

            val command = CreateAccount("John Doe")

            val pipeline = for {
              a   <- createAccount(command)
              res <- findAccountById(a.id)
            } yield res

            val assertion =
              assertM[BankEnvironment, Either[AccountFailure, Account]](pipeline.either,
                                                                        isRight(equalTo(Account(1, "John Doe"))))

            (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

          },
          testM("Fail to find a nonexistent account") {
            val id       = 1
            val pipeline = findAccountById(id).either

            val assertion = assertM[BankEnvironment, Either[AccountFailure, Account]](
              pipeline,
              isLeft(equalTo(AccountNotFound(id)))
            )

            (testEnv >>= assertion.provide): ZIO[Any, Nothing, TestResult]

          }
        )
      }
    )
