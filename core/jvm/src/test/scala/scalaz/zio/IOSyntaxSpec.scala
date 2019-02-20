package scalaz.zio

import org.scalacheck._
import org.specs2.ScalaCheck
import scalaz.zio.syntax._

class IOCreationEagerSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOEagerSyntaxSpec".title ^ s2"""
   Generate a String:
      `.succeed` extension method returns the same UIO[String] as `IO.succeed` does. $t1
   Generate a String:
      `.fail` extension method returns the same IO[String, Nothing] as `IO.fail` does. $t2
   Generate a String:
      `.ensure` extension method returns the same IO[E, Option[A]] => IO[E, A] as `IO.ensure` does. $t3
    """

  def t1 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.succeed
      b <- IO.succeed(str)
    } yield a must ===(b))
  }

  def t2 = forAll(Gen.alphaStr) { str =>
    unsafeRun(for {
      a <- str.fail.attempt
      b <- IO.fail(str).attempt
    } yield a must ===(b))
  }

  def t3 = forAll(Gen.alphaStr) { str =>
    val ioSome = IO.succeed(Some(42))
    unsafeRun(for {
      a <- str.require(ioSome)
      b <- IO.require(str)(ioSome)
    } yield a must ===(b))
  }

}

class IOCreationLazySyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with GenIO
    with ScalaCheck {
  import Prop.forAll

  def is = "IOLazySyntaxSpec".title ^ s2"""
   Generate a String:
      `.point` extension method returns the same UIO[String] as `IO.point` does. $t1
   Generate a String:
      `.sync` extension method returns the same UIO[String] as `IO.sync` does. $t2
   Generate a String:
      `.sync` extension method returns the same Task[String] as `IO.sync` does. $t4
   Generate a String:
      `.syncCatch` extension method returns the same PartialFunction[Throwable, E] => IO[E, A] as `IO.sync` does. $t5
    """

  def t1 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.succeedLazy
      b <- IO.succeedLazy(lazyStr)
    } yield a must ===(b))
  }

  def t2 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.sync
      b <- IO.defer(lazyStr)
    } yield a must ===(b))
  }

  def t4 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    unsafeRun(for {
      a <- lazyStr.sync
      b <- IO.sync(lazyStr)
    } yield a must ===(b))
  }

  def t5 = forAll(Gen.lzy(Gen.alphaStr)) { lazyStr =>
    val partial: PartialFunction[Throwable, Int] = { case _: Throwable => 42 }
    unsafeRun(for {
      a <- lazyStr.sync.keepSome(partial)
      b <- IO.sync(lazyStr).keepSome(partial)
    } yield a must ===(b))
  }

}

class IOIterableSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with GenIO
    with ScalaCheck {
  def is       = "IOIterableSyntaxSpec".title ^ s2"""
   Generate an Iterable of Char:
      `.mergeAll` extension method returns the same IO[E, B] as `IO.mergeAll` does. $t1
    Generate an Iterable of Char:
      `.parAll` extension method returns the same IO[E, List[A]] as `IO.parAll` does. $t2
    Generate an Iterable of Char:
      `.forkAll` extension method returns the same UIO[Fiber[E, List[A]]] as `IO.forkAll` does. $t3
    Generate an Iterable of Char:
      `.sequence` extension method returns the same IO[E, List[A]] as `IO.sequence` does. $t4
    """
  val TestData = "supercalifragilisticexpialadocious".toList

  def t1 = {
    val ios                          = TestData.map(IO.succeed)
    val zero                         = List.empty[Char]
    def merger[A](as: List[A], a: A) = a :: as
    unsafeRun(for {
      merged1 <- ios.mergeAll(zero)(merger)
      merged2 <- IO.mergeAll(ios)(zero)(merger)
    } yield merged1 must ===(merged2))
  }

  def t2 = {
    val ios = TestData.map(IO.defer(_))
    unsafeRun(for {
      parAll1 <- ios.collectAllPar
      parAll2 <- IO.collectAllPar(ios)
    } yield parAll1 must ===(parAll2))
  }

  def t3 = {
    val ios: Iterable[IO[String, Char]] = TestData.map(IO.defer(_))
    unsafeRun(for {
      f1       <- ios.forkAll
      forkAll1 <- f1.join
      f2       <- IO.forkAll(ios)
      forkAll2 <- f2.join
    } yield forkAll1 must ===(forkAll2))
  }

  def t4 = {
    val ios = TestData.map(IO.defer(_))
    unsafeRun(for {
      sequence1 <- ios.collectAll
      sequence2 <- IO.collectAll(ios)
    } yield sequence1 must ===(sequence2))
  }
}

class IOTuplesSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "IOTupleSpec".title ^ s2"""
   Generate a Tuple2 of (Int, String):
      `.map2` extension method should combine them to an IO[E, Z] with a function (A, B) => Z. $t1
   Generate a Tuple3 of (Int, String, String):
      `.map3` extension method should combine them to an IO[E, Z] with a function (A, B, C) => Z. $t2
   Generate a Tuple4 of (Int, String, String, String):
      `.map4` extension method should combine them to an IO[E, C] with a function (A, B, C, D) => Z. $t3
    """

  def t1 = forAll(Gen.posNum[Int], Gen.alphaStr) { (int: Int, str: String) =>
    def f(i: Int, s: String): String = i.toString + s
    val ios                          = (IO.succeed(int), IO.succeed(str))
    unsafeRun(for {
      map2 <- ios.map2[String](f)
    } yield map2 must ===(f(int, str)))
  }

  def t2 = forAll(Gen.posNum[Int], Gen.alphaStr, Gen.alphaStr) { (int: Int, str1: String, str2: String) =>
    def f(i: Int, s1: String, s2: String): String = i.toString + s1 + s2
    val ios                                       = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2))
    unsafeRun(for {
      map3 <- ios.map3[String](f)
    } yield map3 must ===(f(int, str1, str2)))
  }

  def t3 = forAll(Gen.posNum[Int], Gen.alphaStr, Gen.alphaStr, Gen.alphaStr) {
    (int: Int, str1: String, str2: String, str3: String) =>
      def f(i: Int, s1: String, s2: String, s3: String): String = i.toString + s1 + s2 + s3
      val ios                                                   = (IO.succeed(int), IO.succeed(str1), IO.succeed(str2), IO.succeed(str3))
      unsafeRun(for {
        map4 <- ios.map4[String](f)
      } yield map4 must ===(f(int, str1, str2, str3)))
  }

}
