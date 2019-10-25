package zio.examples.bank

import zio.examples.bank.domain.{ Account, Operation }
import zio.examples.bank.effect._
import zio.examples.bank.environment.Environments.BankEnvironment
import zio.{ Ref, ZIO }

object TestEnvironment {

  val testEnv: ZIO[Any, Nothing, BankEnvironment] = for {
    accountDb   <- Ref.make(Map.empty[Int, Account])
    operationDb <- Ref.make(Map.empty[Int, Operation])
    env <- ZIO
            .effect(new Logger with AccountRepository with OperationRepository with ZDate {
              override val log: Logger.Effect                          = SilentLogger
              override val accountRepository: AccountRepository.Effect = new AccountRepositoryInMemory(accountDb)
              override val operationRepository: OperationRepository.Effect =
                new OperationRepositoryInMemory(accountDb, operationDb)
              override val date: ZDate.Effect = ZDateLive
            })
            .orDie

  } yield env

}
