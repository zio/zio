package zio.examples

import zio._

object FromFunctionExample extends ZIOAppDefault {
  final case class DatabaseConfig()

  object DatabaseConfig {
    val live = ZLayer.succeed(DatabaseConfig())
  }

  final case class Database(databaseConfig: DatabaseConfig)

  object Database {
    val live: ZLayer[DatabaseConfig, Nothing, Database] =
      ZLayer.fromFunction(Database.apply _)
  }

  final case class Analytics()

  object Analytics {
    val live: ULayer[Analytics] = ZLayer.succeed(Analytics())
  }

  final case class Users(database: Database, analytics: Analytics)

  object Users {
    val live = ZLayer.fromFunction(Users.apply _)
  }

  final case class App(users: Users, analytics: Analytics) {
    def execute: UIO[Unit] =
      ZIO.debug(s"This app is made from ${users} and ${analytics}")
  }

  object App {
    val live = ZLayer.fromFunction(App.apply _)
  }

  def run =
    ZIO
      .serviceWithZIO[App](_.execute)
      // Cannot use `provide` due to this dotty bug: https://github.com/lampepfl/dotty/issues/12498
      .provideLayer(
        (((DatabaseConfig.live >>> Database.live) ++ Analytics.live >>> Users.live) ++ Analytics.live) >>> App.live
      )
}
