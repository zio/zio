package scalaz.zio.stream

import org.specs2.ScalaCheck

import scala.{ Stream => _ }
import scalaz.zio.{ AbstractRTSSpec, GenIO, IO }
import scala.concurrent.duration._
import scalaz.zio.{ AbstractRTSSpec, ExitResult, GenIO, IO }

class StreamSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {

  override val DefaultTimeout = 20.seconds

  import ArbitraryChunk._

  def is = "StreamSpec".title ^ s2"""
  PureStream.filter       $filter
  PureStream.filterProp       $filterProp
  PureStream.dropWhile    $dropWhile
  PureStream.dropWhileProp    $dropWhileProp
  PureStream.takeWhile    $takeWhile
  PureStream.takeWhileProp    $takeWhileProp
  PureStream.map          $map
  PureStream.mapProp          $mapProp
  PureStream.mapConcat    $mapConcat
  Stream.scan             $scan
  PureStream.concatProp    $concatProp
  PureStream.scan         $scan
  Stream.unfold           $unfold
  Stream.unfoldM          $unfoldM
  Stream.range            $range
  Stream.take             $take
  Stream.zipWithIndex     $zipWithIndex
  Stream.++               $concat
  Stream.foreach0         $foreach0
  Stream.foreach          $foreach
  Stream.collect          $collect
  Stream.monadLaw1 $monadLaw1
  Stream.monadLaw2 $monadLaw2
  Stream.monadLaw3 $monadLaw3
  Stream.forever          $forever
  Stream.joinWith         $joinWith
  Stream.merge            $merge
  Stream.mergeEither      $mergeEither
  Stream.mergeWith        $mergeWith
  Stream.scanM            $scanM
  Stream.transduce        $transduce
  Stream.withEffect       $withEffect
  Stream.zipWith          $zipWith
  Stream.fromIterable     $fromIterable
  Stream.fromChunk        $fromChunk
  Stream.peel             $peel
  """

  import ArbitraryStream._
  import ExitResult._

  private def slurp[E, A](s: Stream[E, A]): ExitResult[E, List[A]] = s match {
    case s: StreamPure[A] =>
      succeeded(s.foldPure(List[A]())((acc, el) => Stream.Step.cont(el :: acc)).extract.reverse)
    case s => slurpM(s)
  }

  private def slurpM[E, A](s: Stream[E, A]): ExitResult[E, List[A]] =
    unsafeRunSync {
      s.fold(List[A]())((acc, el) => IO.now(Stream.Step.cont(el :: acc))).map(str => str.extract.reverse)
    }

  private def filter = {
    val stream = Stream(1, 2, 3, 4, 5).filter(_ % 2 == 0)
    (slurp(stream) must_=== Succeeded(List(2, 4))) and (slurpM(stream) must_=== Succeeded(List(2, 4)))
  }

  private def filterProp =
    prop { (s: Stream[String, String], p: String => Boolean) => slurpM(s.filter(p)) must_=== slurpM(s).map(_.filter(p))
    }

  private def dropWhile = {
    val stream = Stream(1, 1, 1, 3, 4, 5).dropWhile(_ == 1)
    slurp(stream) must_=== Succeeded(List(3, 4, 5))
  }

  private def dropWhileProp =
    prop { (s: Stream[String, String], p: String => Boolean) =>
      slurpM(s.dropWhile(p)) must_=== slurpM(s).map(_.dropWhile(p))
    }

  private def takeWhile = {
    val stream = Stream(3, 4, 5, 1, 1, 1).takeWhile(_ != 1)
    slurp(stream) must_=== Succeeded(List(3, 4, 5))
  }

  private def takeWhileProp =
    prop { (s: Stream[String, String], p: String => Boolean) =>
      val streamTakeWhile = slurpM(s.takeWhile(p))
      val listTakeWhile   = slurpM(s).map(_.takeWhile(p))
      listTakeWhile.succeeded ==> (streamTakeWhile must_=== listTakeWhile)
    }

  private def map = {
    val stream = Stream(1, 1, 1).map(_ + 1)
    slurp(stream) must_=== Succeeded(List(2, 2, 2))
  }

  private def mapProp =
    prop { (s: Stream[String, String], f: String => Int) => slurpM(s.map(f)) must_=== slurpM(s).map(_.map(f))
    }

  private def concatProp =
    prop { (s1: Stream[String, String], s2: Stream[String, String]) =>
      val listConcat = (slurpM(s1) zip slurpM(s2)).map {
        case (left, right) => left ++ right
      }
      val streamConcat = slurpM(s1 ++ s2)
      (streamConcat.succeeded && listConcat.succeeded) ==> (streamConcat must_=== listConcat)
    }

  private def mapConcat = {
    import ArbitraryChunk._
    prop { (s: Stream[String, String], f: String => Chunk[Int]) =>
      slurp(s.mapConcat(f)) must_=== slurpM(s).map(_.flatMap(v => f(v).toSeq))
    }
  }

  private def zipWithIndex =
    prop((s: Stream[String, String]) => slurp(s.zipWithIndex) must_=== slurp(s).map(_.zipWithIndex))

  private def scan = {
    val stream = Stream(1, 1, 1).scan(0)((acc, el) => (acc + el, acc + el))
    slurp(stream) must_=== Succeeded(List(1, 2, 3))
  }

  private def scanM = {
    val stream = Stream(1, 1, 1).scanM(0)((acc, el) => IO.now((acc + el, acc + el)))
    (slurp(stream) must_=== Succeeded(List(1, 2, 3))) and (slurpM(stream) must_=== Succeeded(List(1, 2, 3)))
  }

  private def unfold = {
    val s = Stream.unfold(0)(i => if (i < 10) Some((i, i + 1)) else None)
    slurp(s) must_=== Succeeded((0 to 9).toList) and (slurpM(s) must_=== Succeeded((0 to 9).toList))
  }

  private def unfoldM = {
    val s = Stream.unfoldM(0)(i => if (i < 10) IO.now(Some((i, i + 1))) else IO.now(None))
    slurp(s) must_=== Succeeded((0 to 9).toList) and (slurpM(s) must_=== Succeeded((0 to 9).toList))
  }

  private def range = {
    val s = Stream.range(0, 9)
    slurp(s) must_=== Succeeded((0 to 9).toList) and (slurpM(s) must_=== Succeeded((0 to 9).toList))
  }

  private def take =
    prop { (s: Stream[String, String], n: Int) =>
      val takeStreamResult = slurp(s.take(n))
      val takeListResult      = slurp(s).map(_.take(n))
      (takeListResult.succeeded ==> (takeStreamResult must_=== takeListResult)) &&
        ((!takeStreamResult.succeeded) ==> (!takeListResult.succeeded))
    }

  private def concat =
    prop { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
      val concatStreams = slurp(s1 ++ s2)
      val concatResults = (slurp(s1) zip slurp(s2)).map(t => t._1 ++ t._2)
      (concatStreams.succeeded && concatResults.succeeded) ==> (concatStreams must_=== concatResults)
    }

  private def foreach0 = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1, 1)

    unsafeRun(
      s.foreach0(
        a =>
          IO.sync(
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

    unsafeRun(s.foreach(a => IO.sync(sum += a)))
    sum must_=== 5
  }

  private def collect = {
    val s = Stream(Left(1), Right(2), Left(3)).collect {
      case Right(n) => n
    }

    slurp(s) must_=== Succeeded(List(2)) //and (slurpM(s) must_=== List(2))
  }

  private def monadLaw1 =
    prop((x: Int, f: Int => Stream[String, Int]) => slurp(Stream(x).flatMap(f)) must_=== slurp(f(x)))

  private def monadLaw2 =
    prop((m: Stream[String, Int]) => slurp(m.flatMap(i => Stream(i))) must_=== slurp(m))

  private def monadLaw3 =
    prop { (m: Stream[String, Int], f: Int => Stream[String, Int], g: Int => Stream[String, Int]) =>
      val leftStream  = m.flatMap(f).flatMap(g)
      val rightStream = m.flatMap(x => f(x).flatMap(g))
      slurp(leftStream) must_=== slurp(rightStream)
    }

  private def forever = {
    var sum = 0
    val s = Stream(1).forever.foreach0(
      a =>
        IO.sync {
          sum += a; if (sum >= 9) false else true
      }
    )

    unsafeRun(s)
    sum must_=== 9
  }

  private def joinWith = {
    val s1 = Stream(1, 1)
    val s2 = Stream(2, 2)
    val join = s1.joinWith(s2, 1, 1)(
      (a: IO[Nothing, Option[Int]], b: IO[Nothing, Option[Int]]) =>
        a.seqWith(b)((a, b) => a flatMap (a => b map ((a, _))))
    )

    slurp(join) must_=== Succeeded(List((1, 2), (1, 2)))
  }

  private def merge =
    prop { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
      val mergedStream = slurp(s1 merge s2).map(_.toSet)
      val mergedLists  = (slurp(s1) zip slurp(s2)).map { case (left, right) => left ++ right }.map(_.toSet)
      (!mergedStream.succeeded && !mergedLists.succeeded) || (mergedStream must_=== mergedLists)
    }

  private def mergeEither = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeEither(s2)

    slurp(merge).toEither.right.get must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  private def mergeWith = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)

    slurp(merge).toEither.right.get must containTheSameElementsAs(List("1", "2", "1", "2"))
  }

  private def transduce = {
    val s          = Stream('1', '2', ',', '3', '4')
    val parser     = Sink.readWhile[Char](_.isDigit).map(_.mkString.toInt) <* Sink.readWhile(_ == ',')
    val transduced = s.transduce(parser)

    slurpM(transduced) must_=== Succeeded(List(12, 34))
  }

  private def peel = {
    val s      = Stream('1', '2', ',', '3', '4')
    val parser = Sink.readWhile[Char](_.isDigit).map(_.mkString.toInt) <* Sink.readWhile(_ == ',')
    val peeled = s.peel(parser).use {
      case (n, rest) =>
        IO.now((n, slurpM(rest)))
    }

    unsafeRun(peeled) must_=== ((12, Succeeded(List('3', '4'))))
  }

  private def withEffect = {
    var sum     = 0
    val s       = Stream(1, 1).withEffect(a => IO.sync(sum += a))
    val slurped = slurp(s)

    (slurped must_=== Succeeded(List(1, 1))) and (sum must_=== 2)
  }

  private def zipWith = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _)))

    slurpM(zipped) must_=== Succeeded(List(2, 4))
  }

  private def fromIterable = prop { l: List[Int] =>
    val s = Stream.fromIterable(l)
    slurpM(s) must_=== Succeeded(l) and (slurp(s) must_=== Succeeded(l))
  }

  private def fromChunk = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    (slurpM(s) must_=== Succeeded(c.toSeq.toList)) and (slurp(s) must_=== Succeeded(c.toSeq.toList))
  }
}
