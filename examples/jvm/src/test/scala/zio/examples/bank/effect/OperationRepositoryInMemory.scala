package zio.examples.bank.effect

import java.time.LocalDate

import zio.examples.bank.domain._
import zio.{ Ref, ZIO }

class OperationRepositoryInMemory(accountDb: Ref[Map[Int, Account]], operationDb: Ref[Map[Int, Operation]])
    extends OperationRepository.Effect {

  private def nextId(): ZIO[Any, Nothing, Int] =
    for {
      map  <- operationDb.get
      next = if (map.isEmpty) 1 else map.keys.max + 1
    } yield next

  private def findAccountById(id: Int): ZIO[Any, Unit, Account] =
    for {
      db <- accountDb.get
      result <- db.get(id) match {
                 case Some(account) => ZIO.succeed(account)
                 case _             => ZIO.fail(())
               }
    } yield result

  override def createOperation(o: CreateOperation): ZIO[Any, Throwable, Operation] =
    for {
      id <- nextId()
      transactions = o.transactions.map(
        t => Transaction(id, t.targetAccount, t.valueInCents, t.action, t.processingDate)
      )
      ownerAccount <- accountDb.get.map(_(o.ownerReference))
      newOperation = Operation.apply(id, o.valueInCents, ownerAccount, o.peerReference, transactions)
      _            <- operationDb.update(s => s.+((id, newOperation)))
    } yield newOperation

  override def getAccountBalance(accountId: Int, date: LocalDate): ZIO[Any, Unit, Balance] =
    for {
      operations <- operationDb.get.map(_.values.toList)
      account    <- findAccountById(accountId)
      transactions = operations
        .flatMap(_.transactions)
        .filter(t => t.targetAccount == account && !t.processingDate.isAfter(date))
      value = transactions.foldRight(0L) { (c, acc) =>
        val value = c.action match {
          case Credit => c.valueInCents
          case Debit  => c.valueInCents * -1
        }
        value + acc
      }
    } yield Balance(value, account)

  override def listOperations(accountId: Int): ZIO[Any, Unit, List[Operation]] =
    operationDb.get.map(_.values.filter(_.ownerAccount.id == accountId).toList)

}
