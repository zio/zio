package zio.test

import zio._

object ScopeReproducerSpec extends ZIOSpec[Any]{
  val bootstrap = ZLayer.empty

  def spec = test("scope")(
    for {
      _ <- ZIO.scope
    } yield assertCompletes
  )

}
