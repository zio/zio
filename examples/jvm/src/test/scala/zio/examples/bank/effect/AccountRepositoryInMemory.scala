package zio.examples.bank.effect
import zio.examples.bank.domain.{ Account, CreateAccount }
import zio.{ Ref, ZIO }

class AccountRepositoryInMemory(dbRef: Ref[Map[Int, Account]]) extends AccountRepository.Effect {

  private def nextId(): ZIO[Any, Nothing, Int] =
    for {
      map  <- dbRef.get
      next = if (map.isEmpty) 1 else map.keys.max + 1
    } yield next

  override def createAccount(accountCommand: CreateAccount): ZIO[Any, Throwable, Account] =
    for {
      id         <- nextId()
      newAccount = Account(id, accountCommand.ownerName)
      _          <- dbRef.update(s => s.+((id, newAccount)))

    } yield newAccount

  override def findAccountById(id: Int): ZIO[Any, Unit, Account] =
    for {
      db <- dbRef.get
      result <- db.get(id) match {
                 case Some(account) => ZIO.succeed(account)
                 case _             => ZIO.fail(())
               }
    } yield result

}
