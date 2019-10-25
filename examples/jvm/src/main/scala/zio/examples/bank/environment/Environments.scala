package zio.examples.bank.environment

import zio.examples.bank.effect.{ AccountRepository, Logger, OperationRepository, ZDate }

object Environments {

  type BankEnvironment      = Logger with AccountRepository with OperationRepository with ZDate
  type AccountEnvironment   = Logger with AccountRepository
  type OperationEnvironment = Logger with AccountRepository with OperationRepository with ZDate

}
