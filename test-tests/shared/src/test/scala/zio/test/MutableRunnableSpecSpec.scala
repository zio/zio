package zio.test

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.{Has, Ref, UIO, ZIO, ZLayer}

// because of bug in Tag with Scala 3
// otherwise the environment can be simplified to Has[Ref[Int]]
trait RefInt {
  def get: UIO[Int]
  def inc: UIO[Unit]
}

object RefInt {
  val live: ZLayer[Any, Nothing, Has[RefInt]] = ZLayer.fromEffect(
    Ref
      .make(0)
      .map(ref =>
        new RefInt {
          override def get: UIO[Int] = ref.get

          override def inc: UIO[Unit] = ref.update(_ + 1)
        }
      )
  )
}

object MutableRunnableSpecSpec
    extends MutableRunnableSpec[MutableRunnableSpecSpecCompat.Environment](
      RefInt.live,
      sequential >>> samples(10) >>> before(ZIO.service[RefInt].flatMap(_.inc))
    ) {
  testM("ref 1") {
    assertM(ZIO.service[RefInt].flatMap(_.get))(equalTo(1))
  }

  testM("ref 2") {
    assertM(ZIO.service[RefInt].flatMap(_.get))(equalTo(2))
  }

  testM("check samples") {
    for {
      ref   <- ZIO.service[RefInt]
      _     <- checkM(Gen.anyInt.noShrink)(_ => assertM(ref.inc)(anything))
      value <- ref.get
    } yield assert(value)(equalTo(13))
  }
}

object MutableRunnableSpecSpecCompat {
  type Environment = Has[RefInt]
}
