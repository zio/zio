package zio.examples

import zio._

object FromFunctionExample extends ZIOAppDefault with FromFunctionExampleTypes {

  def run =
    ZIO
      .serviceWithZIO[App](_.execute)
      .provide(
        App.live,
        Users.live,
        Database.live,
        Analytics.live,
        DatabaseConfig.live
      )
}
