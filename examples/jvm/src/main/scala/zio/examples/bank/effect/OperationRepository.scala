package zio.examples.bank.effect

import java.time.LocalDate

import zio.ZIO
import zio.examples.bank.domain.{ Balance, CreateOperation, Operation }

trait OperationRepository {

  val operationRepository: OperationRepository.Effect

}

object OperationRepository {

  trait Effect {

    def createOperation(operation: CreateOperation): ZIO[Any, Throwable, Operation]

    /**
     * Transactions.processingDate <= Date
     */
    def getAccountBalance(accountId: Int, date: LocalDate): ZIO[Any, Unit, Balance]

    def listOperations(accountId: Int): ZIO[Any, Unit, List[Operation]]

  }

}

object OperationRepositoryImpl extends OperationRepository.Effect {

  override def createOperation(operation: CreateOperation): ZIO[Any, Throwable, Operation] =
    ZIO.dieMessage("Not implemented")

  override def getAccountBalance(accountId: Int, date: LocalDate): ZIO[Any, Unit, Balance] =
    ZIO.dieMessage("Not implemented")

  override def listOperations(accountId: Int): ZIO[Any, Unit, List[Operation]] =
    ZIO.dieMessage("Not implemented")
}
