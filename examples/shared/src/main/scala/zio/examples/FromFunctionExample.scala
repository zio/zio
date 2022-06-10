package zio.examples

import zio._

object FromFunctionExample extends ZIOAppDefault {
  final case class DatabaseConfig()

  object DatabaseConfig {
    private[examples] val live = ZLayer.succeed(DatabaseConfig())
  }

  trait Database

  final case class DatabaseLive(databaseConfig: DatabaseConfig) extends Database

  object DatabaseLive {
    private[examples] val layer = ZLayer.fromFunction[Database](apply _)
  }

  trait Analytics

  final case class AnalyticsLive() extends Analytics

  object AnalyticsLive {
    private[examples] val layer = ZLayer.fromFunction[Analytics](apply _)
  }

  trait Users

  final case class UsersLive(database: Database, analytics: Analytics) extends Users

  object UsersLive {
    private[examples] val layer = ZLayer.fromFunction[Users](apply _)
  }

  final case class App(users: Users, analytics: Analytics) {
    def execute: UIO[Unit] =
      ZIO.debug(s"This app is made from ${users} and ${analytics}")
  }

  object App {
    private[examples] val live = ZLayer.fromFunction(App.apply _)
  }

  def run =
    ZIO
      .serviceWithZIO[App](_.execute)
      // Cannot use `provide` due to this dotty bug: https://github.com/lampepfl/dotty/issues/12498
      .provideLayer(
        (((DatabaseConfig.live >>> DatabaseLive.layer) ++ AnalyticsLive.layer >>> UsersLive.layer) ++ AnalyticsLive.layer) >>> App.live
      )
}
