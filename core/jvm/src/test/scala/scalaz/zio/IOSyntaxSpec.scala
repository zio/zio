package scalaz.zio

import org.scalacheck._
import org.specs2.ScalaCheck
import scalaz.zio.syntax._

class IOCreationEagerSyntaxSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOEagerSyntaxSpec".title ^ s2"""
   Generate a String:
      `.now` extension method returns the same IO[Nothing, String] as `IO.now` does. $t1
   Generate a String:
      `.fail` extension method returns the same IO[String, Nothing] as `IO.fail` does. $t2
   Generate a String:
      `.ensure` extension method returns the same IO[E, Option[A]] => IO[E, A] as `IO.ensure` does. $t3
    """

  def t1 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.now
      b <- IO.now(str)
    } yield a must ===(b))
  }

  def t2 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.fail.attempt
      b <- IO.fail(str).attempt
    } yield a must ===(b))
  }

  def t3 = forAll(Gen.alphaStr) { str =>
    val ioSome = IO.now(Some(42))
    unsafeRun(for {
      a <- str.ensure(ioSome)
      b <- IO.ensure(ioSome)
    } yield a must ===(b))
  }

}

class IOCreationLazySyntaxSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOEagerSyntaxSpec".title ^ s2"""
   Generate a String:
      `.point` extension method returns the same IO[Nothing, String] as `IO.point` does. $t1
   Generate a String:
      `.sync` extension method returns the same IO[Nothing, String] as `IO.sync` does. $t2
   Generate a String:
      `.syncException` extension method returns the same IO[Exception, String] as `IO.syncException` does. $t3
   Generate a String:
      `.syncThrowable` extension method returns the same IO[Throwable, String] as `IO.syncThrowable` does. $t4
   Generate a String:
      `.syncCatch` extension method returns the same PartialFunction[Throwable, E] => IO[E, A] as `IO.syncThrowable` does. $t5
    """

  def t1 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.point
      b <- IO.point(lazyStr)
    } yield a must ===(b))
  }

  def t2 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.sync
      b <- IO.sync(lazyStr)
    } yield a must ===(b))
  }

  def t3 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.syncException
      b <- IO.syncException(lazyStr)
    } yield a must ===(b))
  }

  def t4 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.syncThrowable
      b <- IO.syncThrowable(lazyStr)
    } yield a must ===(b))
  }

  def t5 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    val partial: PartialFunction[Throwable, Int] = { case _: Throwable => 42 }
    unsafeRun(for {
      a <- lazyStr.syncCatch[Int](partial)
      b <- IO.syncCatch(lazyStr)(partial)
    } yield a must ===(b))
  }

}
