package zio.examples.bank.effect

import zio.ZIO
import zio.examples.bank.domain.{ Balance, CreateOperation, Operation }

trait OperationRepository {

  val operationRepository: OperationRepository.Effect

}

object OperationRepository {

  trait Effect {

    def createOperation(operation: CreateOperation): ZIO[Any, Throwable, Operation]

    def getAccountBalance(accountId: Int): ZIO[Any, Unit, Balance]

    def listOperations(accountId: Int): ZIO[Any, Unit, List[Operation]]

  }

}

object OperationRepositoryImpl extends OperationRepository.Effect {

  override def createOperation(operation: CreateOperation): ZIO[Any, Throwable, Operation] =
    ZIO.fail(new Exception("Not implemented"))

  override def getAccountBalance(accountId: Int): ZIO[Any, Unit, Balance] = ZIO.fail(())

  override def listOperations(accountId: Int): ZIO[Any, Unit, List[Operation]] = ZIO.fail(())
}
