package zio.examples.bank

import zio.examples.bank.effect.{ AccountRepository, AccountRepositoryInMemory, Logger, SilentLogger }
import zio.examples.bank.environment.Environments.BankEnvironment

object TestEnvironment {

  val env: BankEnvironment = new Logger with AccountRepository {
    override val log: Logger.Effect                          = SilentLogger
    override val accountRepository: AccountRepository.Effect = AccountRepositoryInMemory
  }

}
