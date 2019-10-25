package zio.examples.bank.service

import zio.ZIO
import zio.examples.bank.domain.{ Balance, CreateOperation, Operation }
import zio.examples.bank.environment.Environments.OperationEnvironment
import zio.examples.bank.failure.{ OperationFailure, OperationNotFoundAccount }

trait OperationService[R <: OperationEnvironment] {

  def createOperation(command: CreateOperation): ZIO[R, OperationFailure, Operation]

  def findBalance(accountReference: Int): ZIO[R, OperationFailure, Balance]

  def listOperations(accountReference: Int): ZIO[R, OperationFailure, List[Operation]]

}

object OperationServiceImpl extends OperationService[OperationEnvironment] {

  override def createOperation(command: CreateOperation): ZIO[OperationEnvironment, OperationFailure, Operation] =
    ZIO.accessM { env =>
      import env.accountRepository._
      import zio.examples.bank.service.pure.OperationPure._

      /* Scalaz or Cats (>>=)
       * amountIsValid(_) >>= numberOfTransactions >>= transactionsAreValid >>= transactionsSumEqualsAmount
       */
      val pure: CreateOperation => Either[OperationFailure, CreateOperation] = valueIsValid(_)
        .flatMap(numberOfTransactions)
        .flatMap(transactionsAreValid)
        .flatMap(transactionsSumEqualsAmount)

      val pipeline = for {

        ownerBalance <- findBalance(command.ownerReference)
                         .mapError(_ => OperationNotFoundAccount(command.ownerReference, Some("owner")))
        _ <- findAccountById(command.peerReference)
              .mapError(_ => OperationNotFoundAccount(command.peerReference, Some("peer")))
        _ <- ZIO.fromEither(pure(command)) *> ZIO.fromEither(
              accountAmountIsEnough(ownerBalance, command.valueInCents, command.isExternal)
            )
        res <- env.operationRepository.createOperation(command).orDie
      } yield res

      pipeline

    }

  override def findBalance(accountReference: Int): ZIO[OperationEnvironment, OperationFailure, Balance] =
    ZIO.accessM { env =>
      for {
        today <- env.clock.currentDateTime.map(_.toLocalDate)
        result <- env.operationRepository
                   .getAccountBalance(accountReference, today)
                   .mapError(_ => OperationNotFoundAccount(accountReference, None))
      } yield result
    }

  override def listOperations(accountReference: Int): ZIO[OperationEnvironment, OperationFailure, List[Operation]] =
    ZIO.accessM(
      _.operationRepository
        .listOperations(accountReference)
        .mapError(_ => OperationNotFoundAccount(accountReference, None))
    )

}
