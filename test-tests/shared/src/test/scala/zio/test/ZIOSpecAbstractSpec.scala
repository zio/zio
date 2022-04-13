package zio.test
import zio.{Scope, ZIO}

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  override def spec = for {
    _ <- ZIO.debug("")
  } yield assertCompletes
}
