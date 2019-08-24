package zio.examples.bank.failure

trait AccountFailure

case class AccountNotFound(id: Int) extends AccountFailure
