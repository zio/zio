import zio._
import zio.test._
import zio.stream._

object Reproducer extends ZIOAppDefault {
  val bugGen = for {
    id <- Gen.uuid
    _  <- Gen.fromIterable(LazyList.iterate(0)(_ + 1))
  } yield id

  val constGen = for {
    id <- Gen.uuid
    _  <- Gen.const(1)
  } yield id

  val workingGen = for {
    i  <- Gen.fromIterable(LazyList.iterate(0)(_ + 1))
    id <- Gen.uuid
  } yield id

  def run = for {
    bugValues <- bugGen.runCollectN(5)
    _ <- ZIO.debug(s"Bug generated UUIDs: $bugValues")
    constValues <- constGen.runCollectN(5)
    _ <- ZIO.debug(s"Const generated UUIDs: $constValues")
    workingValues <- workingGen.runCollectN(5)
    _ <- ZIO.debug(s"Working generated UUIDs: $workingValues")
  } yield ()
}
