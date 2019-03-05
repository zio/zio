package scalaz.zio.stream

import org.specs2.ScalaCheck
import scala.{ Stream => _ }
import scalaz.zio.{ Chunk, Exit, GenIO, IO, Queue, TestRuntime }
import scala.concurrent.duration._
import scalaz.zio.QueueSpec.waitForSize

class StreamSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with StreamTestUtils
    with GenIO
    with ScalaCheck {

  override val DefaultTimeout = 20.seconds

  import ArbitraryChunk._

  def is = "StreamSpec".title ^ s2"""
  PureStream.filter         $filter
  PureStream.dropWhile      $dropWhile
  PureStream.takeWhile      $takeWhile
  PureStream.mapProp        $map
  PureStream.mapConcat      $mapConcat
  Stream.filterM            $filterM
  Stream.scan               $mapAccum
  Stream.++                 $concat
  Stream.unfold             $unfold
  Stream.unfoldM            $unfoldM
  Stream.range              $range
  Stream.take               $take
  Stream.foreach0           $foreach0
  Stream.foreach            $foreach
  Stream.collect            $collect
  Stream.forever            $forever
  Stream.scanM              $mapAccumM
  Stream.transduce          $transduce
  Stream.withEffect         $withEffect
  Stream.fromIterable       $fromIterable
  Stream.fromChunk          $fromChunk
  Stream.fromQueue          $fromQueue
  Stream.toQueue            $toQueue
  Stream.peel               $peel

  Stream merging
    merge                   $merge
    mergeEither             $mergeEither
    mergeWith               $mergeWith
    mergeWith short circuit $mergeWithShortCircuit

  Stream zipping
    zipWith                     $zipWith
    zipWithIndex                $zipWithIndex
    zipWith ignore RHS          $zipWithIgnoreRhs
    zipWith prioritizes failure $zipWithPrioritizesFailure

  Stream monad laws
    left identity           $monadLaw1
    right identity          $monadLaw2
    associativity           $monadLaw3

  Stream stack safety
    deep flatMap            $deepFlatMap
  """

  import ArbitraryStream._
  import Exit._

  private def filter =
    prop { (s: Stream[Any, String, String], p: String => Boolean) =>
      slurp(s.filter(p)) must_=== slurp(s).map(_.filter(p))
    }

  private def filterM =
    prop { (s: Stream[Any, String, String], p: String => Boolean) =>
      slurp(s.filterM(s => IO.succeed(p(s)))) must_=== slurp(s).map(_.filter(p))
    }

  private def dropWhile =
    prop { (s: Stream[Any, String, String], p: String => Boolean) =>
      slurp(s.dropWhile(p)) must_=== slurp(s).map(_.dropWhile(p))
    }

  private def takeWhile =
    prop { (s: Stream[Any, String, String], p: String => Boolean) =>
      val streamTakeWhile = slurp(s.takeWhile(p))
      val listTakeWhile   = slurp(s).map(_.takeWhile(p))
      listTakeWhile.succeeded ==> (streamTakeWhile must_=== listTakeWhile)
    }

  private def map =
    prop { (s: Stream[Any, String, String], f: String => Int) =>
      slurp(s.map(f)) must_=== slurp(s).map(_.map(f))
    }

  private def concat =
    prop { (s1: Stream[Any, String, String], s2: Stream[Any, String, String]) =>
      val listConcat = (slurp(s1) zip slurp(s2)).map {
        case (left, right) => left ++ right
      }
      val streamConcat = slurp(s1 ++ s2)
      (streamConcat.succeeded && listConcat.succeeded) ==> (streamConcat must_=== listConcat)
    }

  private def mapConcat = {
    import ArbitraryChunk._
    prop { (s: Stream[Any, String, String], f: String => Chunk[Int]) =>
      slurp(s.mapConcat(f)) must_=== slurp(s).map(_.flatMap(v => f(v).toSeq))
    }
  }

  private def mapAccum = {
    val stream = Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el))
    slurp(stream) must_=== Success(List(1, 2, 3))
  }

  private def mapAccumM = {
    val stream = Stream(1, 1, 1).mapAccumM(0)((acc, el) => IO.succeed((acc + el, acc + el)))
    (slurp(stream) must_=== Success(List(1, 2, 3))) and (slurp(stream) must_=== Success(List(1, 2, 3)))
  }

  private def unfold = {
    val s = Stream.unfold(0)(i => if (i < 10) Some((i, i + 1)) else None)
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def unfoldM = {
    val s = Stream.unfoldM(0)(i => if (i < 10) IO.succeed(Some((i, i + 1))) else IO.succeed(None))
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def range = {
    val s = Stream.range(0, 9)
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def take =
    prop { (s: Stream[Any, String, String], n: Int) =>
      val takeStreamResult = slurp(s.take(n))
      val takeListResult   = slurp(s).map(_.take(n))
      (takeListResult.succeeded ==> (takeStreamResult must_=== takeListResult)) //&&
    // ((!takeStreamResult.succeeded) ==> (!takeListResult.succeeded))
    }

  private def foreach0 = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1, 1)

    unsafeRun(
      s.foreachWhile[Any, Nothing](
        a =>
          IO.effectTotal(
            if (sum >= 3) false
            else {
              sum += a;
              true
            }
          )
      )
    )
    sum must_=== 3
  }

  private def foreach = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1)

    unsafeRun(s.foreach[Any, Nothing](a => IO.effectTotal(sum += a)))
    sum must_=== 5
  }

  private def collect = {
    val s = Stream(Left(1), Right(2), Left(3)).collect {
      case Right(n) => n
    }

    slurp(s) must_=== Success(List(2)) and (slurp(s) must_=== Success(List(2)))
  }

  private def monadLaw1 =
    prop((x: Int, f: Int => Stream[Any, String, Int]) => slurp(Stream(x).flatMap(f)) must_=== slurp(f(x)))

  private def monadLaw2 =
    prop((m: Stream[Any, String, Int]) => slurp(m.flatMap(i => Stream(i))) must_=== slurp(m))

  private def monadLaw3 =
    prop { (m: Stream[Any, String, Int], f: Int => Stream[Any, String, Int], g: Int => Stream[Any, String, Int]) =>
      val leftStream  = m.flatMap(f).flatMap(g)
      val rightStream = m.flatMap(x => f(x).flatMap(g))
      slurp(leftStream) must_=== slurp(rightStream)
    }

  private def deepFlatMap = {
    def fib(n: Int): Stream[Any, Nothing, Int] =
      if (n <= 1) Stream.succeedLazy(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap { b =>
            Stream.succeedLazy(a + b)
          }
        }

    val stream   = fib(20)
    val expected = 6765

    slurp(stream).toEither must beRight(List(expected))
  }

  private def forever = {
    var sum = 0
    val s = Stream(1).forever.foreachWhile[Any, Nothing](
      a =>
        IO.effectTotal {
          sum += a; if (sum >= 9) false else true
        }
    )

    unsafeRun(s)
    sum must_=== 9
  }

  private def merge =
    prop { (s1: Stream[Any, String, Int], s2: Stream[Any, String, Int]) =>
      val mergedStream = slurp(s1 merge s2).map(_.toSet)
      val mergedLists  = (slurp(s1) zip slurp(s2)).map { case (left, right) => left ++ right }.map(_.toSet)
      (!mergedStream.succeeded && !mergedLists.succeeded) || (mergedStream must_=== mergedLists)
    }

  private def mergeEither = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeEither(s2)
    val list: List[Either[Int, Int]] = slurp(merge).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  private def mergeWith = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = slurp(merge).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List("1", "2", "1", "2"))
  }

  private def mergeWithShortCircuit = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = slurp0(merge)(_ => false).toEither.fold(
      _ => List("9"),
      identity
    )

    list must_=== List()
  }

  private def transduce = {
    val s          = Stream('1', '2', ',', '3', '4')
    val parser     = Sink.readWhile[Char](_.isDigit).map(_.mkString.toInt) <* Sink.readWhile(_ == ',')
    val transduced = s.transduce(parser)

    slurp(transduced) must_=== Success(List(12, 34))
  }

  private def peel = {
    val s      = Stream('1', '2', ',', '3', '4')
    val parser = Sink.readWhile[Char](_.isDigit).map(_.mkString.toInt) <* Sink.readWhile(_ == ',')
    val peeled = s.peel(parser).use[Any, Int, (Int, Exit[Nothing, List[Char]])] {
      case (n, rest) =>
        IO.succeed((n, slurp(rest)))
    }

    unsafeRun(peeled) must_=== ((12, Success(List('3', '4'))))
  }

  private def withEffect = {
    var sum     = 0
    val s       = Stream(1, 1).withEffect[Any, Nothing](a => IO.effectTotal(sum += a))
    val slurped = slurp(s)

    (slurped must_=== Success(List(1, 1))) and (sum must_=== 2)
  }

  private def zipWith = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _)))

    slurp(zipped) must_=== Success(List(2, 4))
  }

  private def zipWithIndex =
    prop((s: Stream[Any, String, String]) => slurp(s.zipWithIndex) must_=== slurp(s).map(_.zipWithIndex))

  private def zipWithIgnoreRhs = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, _) => a)

    slurp(zipped) must_=== Success(List(1, 2, 3))
  }

  private def zipWithPrioritizesFailure = {
    val s1 = Stream.never
    val s2 = Stream.fail("Ouch")

    slurp(s1.zipWith(s2)((_, _) => None)) must_=== Exit.fail("Ouch")
  }

  private def fromIterable = prop { l: List[Int] =>
    val s = Stream.fromIterable(l)
    slurp(s) must_=== Success(l) and (slurp(s) must_=== Success(l))
  }

  private def fromChunk = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    (slurp(s) must_=== Success(c.toSeq.toList)) and (slurp(s) must_=== Success(c.toSeq.toList))
  }

  private def fromQueue = prop { c: Chunk[Int] =>
    val result = unsafeRunSync {
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offerAll(c.toSeq)
        s     = Stream.fromQueue(queue)
        fiber <- s.fold[Any, Nothing, Int, List[Int]].flatMap { f0 =>
                  f0(List[Int](), _ => true, (acc, el) => IO.succeed(el :: acc))
                    .map(_.reverse)
                    .fork
                }
        _     <- waitForSize(queue, -1)
        _     <- queue.shutdown
        items <- fiber.join
      } yield items
    }
    result must_=== Success(c.toSeq.toList)
  }

  private def toQueue = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    val result = unsafeRunSync {
      s.toQueue(1000).use { queue: Queue[Take[Nothing, Int]] =>
        waitForSize(queue, c.length + 1) *> queue.takeAll
      }
    }
    result must_=== Success(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End)
  }
}
