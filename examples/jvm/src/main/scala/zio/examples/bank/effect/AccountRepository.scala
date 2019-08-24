package zio.examples.bank.effect

import zio.examples.bank.domain.{ Account, CreateAccount }
import zio.ZIO

trait AccountRepository {

  val accountRepository: AccountRepository.Effect

}

object AccountRepository {

  trait Effect {

    def createAccount(account: CreateAccount): ZIO[Any, Throwable, Account]

    /*
     * ZIO[Any, Nothing, Option[Account]] and ZIO[Any, Unit, Account] are isomorphic.
     */
    def findAccountById(id: Int): ZIO[Any, Unit, Account]

  }

}

object AccountRepositoryImpl extends AccountRepository.Effect {

  def createAccount(account: CreateAccount): ZIO[Any, Throwable, Account] = ZIO.fail(new Exception("Not implemented"))

  override def findAccountById(id: Int): ZIO[Any, Unit, Account] = ZIO.fail(())

}
