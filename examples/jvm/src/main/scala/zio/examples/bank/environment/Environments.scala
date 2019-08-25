package zio.examples.bank.environment

import zio.examples.bank.effect.{ AccountRepository, Logger, OperationRepository }

object Environments {

  type BankEnvironment      = Logger with AccountRepository with OperationRepository
  type AccountEnvironment   = Logger with AccountRepository
  type OperationEnvironment = Logger with AccountRepository with OperationRepository

}
