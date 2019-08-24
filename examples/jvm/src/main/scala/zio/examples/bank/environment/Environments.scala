package zio.examples.bank.environment

import zio.examples.bank.effect.{ AccountRepository, Logger }

object Environments {

  type BankEnvironment    = Logger with AccountRepository
  type AccountEnvironment = Logger with AccountRepository

}
