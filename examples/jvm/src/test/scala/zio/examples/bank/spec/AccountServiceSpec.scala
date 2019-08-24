package zio.examples.bank.spec

import zio.examples.bank.TestEnvironment
import zio.examples.bank.domain.{ Account, CreateAccount }
import zio.examples.bank.environment.Environments.BankEnvironment
import zio.examples.bank.failure.AccountFailure
import zio.test.Predicate._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec }
import zio.examples.bank.service.AccountServiceImpl._

object AccountServiceSpec
    extends DefaultRunnableSpec(
      suite("AccountSpec")(
        testM("Create Account") {

          val command = CreateAccount("John Doe")

          val pipeline = createAccount(command).either

          assertM[BankEnvironment, Either[AccountFailure, Account]](pipeline, isRight(equalTo(Account(1, "John Doe"))))
            .provide(TestEnvironment.env)

        }
      )
    )
