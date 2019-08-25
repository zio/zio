package zio.examples.bank.failure

import zio.examples.bank.domain.CreateTransaction

sealed trait OperationFailure

case class OperationWithoutTransactions()                                           extends OperationFailure
case class OperationInvalidValue(valueInCents: Long)                                extends OperationFailure
case class OperationValueAndSumOfTransactionsDifferent(expected: Long, sum: Long)   extends OperationFailure
case class OperationNotFoundAccount(accountId: Int, target: Option[String])         extends OperationFailure
case class OperationWithInvalidCreateTransactions(command: List[CreateTransaction]) extends OperationFailure
case class OperationOwnerAccountInsufficientValue(expected: Long, current: Long)    extends OperationFailure
