package zio.examples.bank.failure

sealed trait AccountFailure

case class AccountNotFound(id: Int) extends AccountFailure
