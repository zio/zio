package zio.stream

import com.github.ghik.silencer.silent
import org.specs2.ScalaCheck
import org.specs2.scalacheck.Parameters

import scala.{ Stream => _ }
import scala.concurrent.duration._
import zio.{ Chunk, Exit, GenIO, IO, TestRuntime, ZIO }

import scala.annotation.tailrec

class StreamChunkSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {

  override val DefaultTimeout = 20.seconds

  implicit val params: Parameters = Parameters(maxSize = 10)

  def is = "StreamChunkSpec".title ^ s2"""
  StreamChunk.map            $map
  StreamChunk.filter         $filter
  StreamChunk.filterNot      $filterNot
  StreamChunk.mapConcatChunk $mapConcatChunk
  StreamChunk.mapConcat      $mapConcat
  StreamChunk.drop           $drop
  StreamChunk.dropWhile      $dropWhile
  StreamChunk.take           $take
  StreamChunk.takeWhile      $takeWhile
  StreamChunk.mapAccum       $mapAccum
  StreamChunk.mapM           $mapM
  StreamChunk.++             $concat
  StreamChunk.zipWithIndex   $zipWithIndex
  StreamChunk.foreach0       $foreach0
  StreamChunk.foreach        $foreach
  StreamChunk.monadLaw1      $monadLaw1
  StreamChunk.monadLaw2      $monadLaw2
  StreamChunk.monadLaw3      $monadLaw3
  StreamChunk.tap            $tap
  StreamChunk.foldLeft       $foldLeft
  StreamChunk.fold           $fold
  StreamChunk.flattenChunks  $flattenChunks
  StreamChunk.collect        $collect
  Stream.toInputStream       $toInputStream
  """

  import ArbitraryStreamChunk._
  import Exit._

  private def slurp[E, A](s: StreamChunk[E, A]): Exit[E, Seq[A]] =
    unsafeRunSync {
      s.foldChunks(Chunk.empty: Chunk[A])(_ => true)((acc, el) => IO.succeed(acc ++ el))
        .map(_.toSeq)
    }

  private def map =
    prop { (s: StreamChunk[String, String], f: String => Int) =>
      slurp(s.map(f)) must_=== slurp(s).map(_.map(f))
    }

  private def filter =
    prop { (s: StreamChunk[String, String], p: String => Boolean) =>
      slurp(s.filter(p)) must_=== slurp(s).map(_.filter(p))
    }

  private def filterNot =
    prop { (s: StreamChunk[String, String], p: String => Boolean) =>
      slurp(s.filterNot(p)) must_=== slurp(s).map(_.filterNot(p))
    }

  private def mapConcatChunk = {
    import ArbitraryChunk._
    prop { (s: StreamChunk[String, String], f: String => Chunk[Int]) =>
      slurp(s.mapConcatChunk(f)) must_=== slurp(s).map(_.flatMap(v => f(v).toSeq))
    }
  }

  private def mapConcat =
    prop { (s: StreamChunk[String, String], f: String => Iterable[Int]) =>
      slurp(s.mapConcat(f)) must_=== slurp(s).map(_.flatMap(v => f(v).toSeq))
    }

  private def drop =
    prop { (s: StreamChunk[String, String], n: Int) =>
      slurp(s.drop(n)) must_=== slurp(s).map(_.drop(n))
    }

  private def dropWhile =
    prop { (s: StreamChunk[String, String], p: String => Boolean) =>
      slurp(s.dropWhile(p)) must_=== slurp(s).map(_.dropWhile(p))
    }

  private def take =
    prop { (s: StreamChunk[Nothing, String], n: Int) =>
      slurp(s.take(n)) must_=== slurp(s).map(_.take(n))
    }

  private def takeWhile =
    prop { (s: StreamChunk[Nothing, String], p: String => Boolean) =>
      slurp(s.takeWhile(p)) must_=== slurp(s).map(_.takeWhile(p))
    }

  private def concat =
    prop { (s1: StreamChunk[String, String], s2: StreamChunk[String, String]) =>
      val listConcat = for {
        left  <- slurp(s1)
        right <- slurp(s2)
      } yield left ++ right
      val streamConcat = slurp(s1 ++ s2)
      streamConcat must_=== listConcat
    }

  private def zipWithIndex =
    prop((s: StreamChunk[String, String]) => slurp(s.zipWithIndex) must_=== slurp(s).map(_.zipWithIndex))

  private def mapAccum =
    prop { s: StreamChunk[String, Int] =>
      val slurped = slurp(s.mapAccum(0)((acc, el) => (acc + el, acc + el)))
      slurped must_=== slurp(s).map(_.scanLeft(0)((acc, el) => acc + el).drop(1))
    }

  private def mapM =
    prop { (s: StreamChunk[String, Int], f: Int => Int) =>
      slurp(s.mapM(a => IO.succeed(f(a)))) must_=== slurp(s).map(_.map(f))
    }

  private def foreach0 =
    prop { (s: StreamChunk[String, Int], cont: Int => Boolean) =>
      var acc = List[Int]()

      val result = unsafeRunSync {
        s.foreachWhile { a =>
          IO.effectTotal {
            if (cont(a)) {
              acc ::= a
              true
            } else false
          }
        }
      }

      result.map(_ => acc.reverse) must_=== slurp(s.takeWhile(cont)).map(_.toList)
    }

  private def foreach =
    prop { s: StreamChunk[String, Int] =>
      var acc = List[Int]()

      val result = unsafeRunSync {
        s.foreach(a => IO.effectTotal(acc ::= a))
      }

      result.map(_ => acc.reverse) must_=== slurp(s).map(_.toList)
    }

  private def monadLaw1 =
    prop(
      (x: Int, f: Int => StreamChunk[String, Int]) =>
        slurp(ZStreamChunk.succeed(Chunk(x)).flatMap(f)) must_=== slurp(f(x))
    )

  private def monadLaw2 =
    prop(
      (m: StreamChunk[String, Int]) => slurp(m.flatMap(i => ZStreamChunk.succeed(Chunk(i)))) must_=== slurp(m)
    )

  private def monadLaw3 =
    prop { (m: StreamChunk[String, Int], f: Int => StreamChunk[String, Int], g: Int => StreamChunk[String, Int]) =>
      val leftStream  = m.flatMap(f).flatMap(g)
      val rightStream = m.flatMap(x => f(x).flatMap(g))
      slurp(leftStream) must_=== slurp(rightStream)
    }

  private def tap =
    prop { (s: StreamChunk[String, String]) =>
      val withoutEffect = slurp(s)
      var acc           = List[String]()
      val tap           = slurp(s.tap(a => IO.effectTotal(acc ::= a)))

      (tap must_=== withoutEffect) and
        ((Success(acc.reverse) must_== withoutEffect) when withoutEffect.succeeded)
    }

  private def foldLeft =
    prop { (s: StreamChunk[String, String], zero: Int, f: (Int, String) => Int) =>
      unsafeRunSync(s.foldLeft(zero)(f)) must_=== slurp(s).map(_.foldLeft(zero)(f))
    }

  private def fold =
    prop { (s: StreamChunk[Nothing, String], zero: Int, cont: Int => Boolean, f: (Int, String) => Int) =>
      val streamResult = unsafeRunSync(
        s.fold[Any, Nothing, String, Int](zero)(cont)((acc, a) => IO.succeed(f(acc, a)))
      )
      val listResult = slurp(s).map(l => foldLazyList(l.toList, zero)(cont)(f))
      streamResult must_=== listResult
    }

  private def foldLazyList[S, T](list: List[T], zero: S)(cont: S => Boolean)(f: (S, T) => S): S = {
    @tailrec
    def loop(xs: List[T], state: S): S = xs match {
      case head :: tail if cont(state) => loop(tail, f(state, head))
      case _                           => state
    }
    loop(list, zero)
  }

  private def flattenChunks =
    prop { (s: StreamChunk[String, String]) =>
      val result = unsafeRunSync {
        s.flattenChunks.fold[String, List[String]](Nil)((acc, a) => a :: acc).map(_.reverse)
      }
      result must_== slurp(s)
    }

  private def collect =
    prop { (s: StreamChunk[String, String], p: PartialFunction[String, String]) =>
      slurp(s.collect(p)) must_=== slurp(s).map(_.collect(p))
    }

  @silent("Any")
  def toInputStream = {
    import zio.stream.Stream._
    val stream                                    = StreamChunk.fromChunks(Chunk(1, 2, 3)).map(_.toByte)
    val streamResult: Exit[Throwable, List[Byte]] = unsafeRunSync(stream.flattenChunks.runCollect)
    val inputStreamResult = unsafeRunSync(new ZStreamChunkByteOps[Any, Throwable](stream).toInputStream.use {
      inputStream =>
        ZIO.succeed(
          scala.Stream
            .continually(inputStream.read())
            .takeWhile(_ != -1)
            .map(_.toByte)
            .toList
        )
    })
    streamResult must_=== inputStreamResult
  }
}
