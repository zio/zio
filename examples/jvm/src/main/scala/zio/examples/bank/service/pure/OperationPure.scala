package zio.examples.bank.service.pure

import zio.examples.bank.domain.{ Balance, CreateOperation, Debit }
import zio.examples.bank.failure._

object OperationPure {

  def amountIsValid(o: CreateOperation): Either[OperationInvalidValue, CreateOperation] =
    if (o.valueInCents <= 0) Left(OperationInvalidValue(o.valueInCents)) else Right(o)

  def numberOfTransactions(o: CreateOperation): Either[OperationWithoutTransactions, CreateOperation] =
    if (o.transactions.isEmpty) Left(OperationWithoutTransactions()) else Right(o)

  def transactionsAreValid(
    o: CreateOperation
  ): Either[OperationWithInvalidCreateTransactions, CreateOperation] = {
    val invalidTrs = o.transactions.filter(_.valueInCents <= 0)

    if (invalidTrs.nonEmpty)
      Left(OperationWithInvalidCreateTransactions(invalidTrs))
    else
      Right(o)

  }

  def transactionsSumEqualsAmount(
    o: CreateOperation
  ): Either[OperationValueAndSumOfTransactionsDifferent, CreateOperation] = {
    lazy val trsValue = o.transactions.filter(_.action == Debit).foldRight(0L)(_.valueInCents + _)

    if (o.isExternal && o.transactions.head.valueInCents == o.valueInCents)
      Right(o)
    else if (trsValue == o.valueInCents)
      Right(o)
    else
      Left(OperationValueAndSumOfTransactionsDifferent(o.valueInCents, trsValue))
  }

  def accountAmountIsEnough(balance: Balance,
                            value: Long,
                            isExternal: Boolean): Either[OperationOwnerAccountInsufficientValue, Unit] =
    if (balance.valueInCents < value && !isExternal)
      Left(OperationOwnerAccountInsufficientValue(value, balance.valueInCents))
    else
      Right(())

}
