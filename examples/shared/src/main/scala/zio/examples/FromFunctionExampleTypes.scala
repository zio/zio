package zio.examples

import zio.{UIO, ULayer, ZIO, ZLayer}

trait FromFunctionExampleTypes {
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
    val live: ZLayer[Database with Analytics, Nothing, Users] =
      ZLayer.fromFunction[Database, Analytics, Users](Users.apply _)
  }

  final case class App(users: Users, analytics: Analytics) {
    def execute: UIO[Unit] =
      ZIO.debug(s"This app is made from ${users} and ${analytics}")
  }

  object App {
    val live: ZLayer[Users with Analytics, Nothing, App] =
      ZLayer.fromFunction[Users, Analytics, App](App.apply _)
  }
}
