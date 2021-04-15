package zio.test

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.TestEnvironment
import zio.{Has, Ref, ZIO, ZLayer}

object MutableRunnableSpecSpec
    extends MutableRunnableSpec[MutableRunnableSpecSpecCompat.Environment](
      TestEnvironment.any ++ ZLayer.fromEffect(Ref.make(0)),
      sequential >>> samples(10) >>> before(ZIO.service[Ref[Int]].flatMap(_.update(_ + 1)))
    ) {

  testM("ref 1") {
    assertM(ZIO.service[Ref[Int]].flatMap(_.get))(equalTo(1))
  }

  testM("ref 2") {
    assertM(ZIO.service[Ref[Int]].flatMap(_.get))(equalTo(2))
  }

  testM("check samples") {
    for {
      ref   <- ZIO.service[Ref[Int]]
      _     <- checkM(Gen.anyInt.noShrink)(_ => assertM(ref.update(_ + 1))(anything))
      value <- ref.get
    } yield assert(value)(equalTo(13))
  }
}

object MutableRunnableSpecSpecCompat {
  type Environment = TestEnvironment with Has[Ref[Int]]
}
