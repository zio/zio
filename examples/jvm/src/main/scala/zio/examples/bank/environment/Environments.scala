package zio.examples.bank.environment

import zio.clock.Clock
import zio.examples.bank.effect.{ AccountRepository, Logger, OperationRepository }

object Environments {

  type BankEnvironment      = Clock with Logger with AccountRepository with OperationRepository
  type AccountEnvironment   = Logger with AccountRepository
  type OperationEnvironment = Clock with Logger with AccountRepository with OperationRepository

}
