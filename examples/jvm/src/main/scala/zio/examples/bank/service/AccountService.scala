package zio.examples.bank.service

import zio.examples.bank.domain.{ Account, CreateAccount }
import zio.examples.bank.environment.Environments.AccountEnvironment
import zio.examples.bank.failure.{ AccountFailure, AccountNotFound }
import zio.ZIO

trait AccountService[R <: AccountEnvironment] {

  def createAccount(command: CreateAccount): ZIO[R, AccountFailure, Account]

  def findAccountById(id: Int): ZIO[R, AccountFailure, Account]

}

object AccountServiceImpl extends AccountService[AccountEnvironment] {

  override def createAccount(command: CreateAccount): ZIO[AccountEnvironment, AccountFailure, Account] =
    ZIO.accessM { env =>
      val pipeline = for {
        _       <- info("Creating account")
        account <- env.accountRepository.createAccount(command)
        _       <- info("Account created successfully")
      } yield account

      pipeline.orDie

    }

  override def findAccountById(id: Int): ZIO[AccountEnvironment, AccountFailure, Account] =
    ZIO.accessM { env =>
      val pipeline = for {
        _       <- info(s"Searching user [id: $id] ...")
        account <- env.accountRepository.findAccountById(id)
        _       <- info("Account found successfully")
      } yield account

      pipeline.mapError(_ => AccountNotFound(id))
    }

}
