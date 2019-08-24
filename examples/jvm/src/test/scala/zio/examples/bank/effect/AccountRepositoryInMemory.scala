package zio.examples.bank.effect
import zio.{ Ref, UIO, ZIO }
import zio.examples.bank.domain.{ Account, CreateAccount }

object AccountRepositoryInMemory extends AccountRepository.Effect {

  private val dbRef: UIO[Ref[Map[Int, Account]]] = Ref.make(Map.empty)

  private def nextId(): ZIO[Any, Nothing, Int] =
    for {
      map  <- dbRef.flatMap(_.get)
      next = if (map.isEmpty) 1 else map.keys.max + 1
    } yield next

  override def createAccount(accountCommand: CreateAccount): ZIO[Any, Throwable, Account] =
    for {
      id         <- nextId()
      newAccount = Account(id, accountCommand.ownerName)
      _ <- dbRef.flatMap(
            ref =>
              ref.update { db =>
                db + (id -> newAccount)
            }
          )
    } yield newAccount

  override def findAccountById(id: Int): ZIO[Any, Unit, Account] =
    for {
      db <- dbRef.flatMap(_.get)
      result <- db.get(id) match {
                 case Some(account) => ZIO.succeed(account)
                 case _             => ZIO.fail(())
               }
    } yield result

}
