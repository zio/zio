package dev.zio.quickstart

import dev.zio.quickstart.config.HttpServerConfig
import dev.zio.quickstart.counter.CounterApp
import dev.zio.quickstart.download.DownloadApp
import dev.zio.quickstart.greet.GreetingApp
import dev.zio.quickstart.users.{InmemoryUserRepo, UserApp, UserRepo}
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import zio.http._

import java.net.InetSocketAddress

object MainApp extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider
        .fromResourcePath()
    )

  val myApp: Http[UserRepo with Ref[Int], Nothing, Request, Response] =
    GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp()

  val serverConfig: ZLayer[Any, Config.Error, ServerConfig] =
    ZLayer
      .fromZIO(
        ZIO.config[HttpServerConfig](HttpServerConfig.config).map { c =>
          ServerConfig(
            address = new InetSocketAddress(c.port),
            nThreads = c.nThreads
          )
        }
      )

  def run =
    (Server
      .install(myApp)
      .flatMap(port =>
        Console.printLine(s"Started server on port: $port")
      ) *> ZIO.never)
      .provide(
        serverConfig,
        Server.live,

        // A layer responsible for storing the state of the `counterApp`
        ZLayer.fromZIO(Ref.make(0)),

        // To use the persistence layer, provide the `PersistentUserRepo.layer` layer instead
        InmemoryUserRepo.layer
      )
}
