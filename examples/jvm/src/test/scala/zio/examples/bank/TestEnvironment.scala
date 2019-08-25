package zio.examples.bank

import zio.examples.bank.domain.Account
import zio.examples.bank.effect.{ AccountRepository, AccountRepositoryInMemory, Logger, SilentLogger }
import zio.{ Ref, ZIO }

object TestEnvironment {

  val testEnv: ZIO[Any, Nothing, Logger with AccountRepository] = for {
    account <- Ref.make(Map.empty[Int, Account])
    env <- ZIO
            .effect(new Logger with AccountRepository {
              override val log: Logger.Effect                          = SilentLogger
              override val accountRepository: AccountRepository.Effect = new AccountRepositoryInMemory(account)
            })
            .orDie

  } yield env

}
