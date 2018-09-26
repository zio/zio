package scalaz.zio.stream

import org.specs2.ScalaCheck
import scala.{ Stream => _ }
import scalaz.zio.{ AbstractRTSSpec, GenIO, IO }

class StreamSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "StreamSpec".title ^ s2"""
  PureStream.filter       $filter
  PureStream.dropWhile    $dropWhile
  PureStream.takeWhile    $takeWhile
  PureStream.map          $map
  PureStream.mapConcat    $mapConcat
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
  Stream.flatMap          $flatMap
  Stream.forever          $forever
  Stream.joinWith         $joinWith
  Stream.merge            $merge
  Stream.mergeEither      $mergeEither
  Stream.mergeWith        $mergeWith
  """

  def slurp[E, A](s: Stream[E, A]) = s match {
    case s: StreamPure[A] => s.foldPure(List[A]())((acc, el) => Stream.Step.cont(el :: acc)).extract.reverse
    case s                => slurpM(s)
  }

  def slurpM[E, A](s: Stream[E, A]) =
    unsafeRun(s.fold(List[A]())((acc, el) => IO.now(Stream.Step.cont(el :: acc)))).extract.reverse

  def filter = {
    val stream = Stream(1, 2, 3, 4, 5).filter(_ % 2 == 0)
    (slurp(stream) must_=== List(2, 4)) and (slurpM(stream) must_=== List(2, 4))
  }

  def dropWhile = {
    val stream = Stream(1, 1, 1, 3, 4, 5).dropWhile(_ == 1)
    (slurp(stream) must_=== List(3, 4, 5)) and (slurpM(stream) must_=== List(3, 4, 5))
  }

  def takeWhile = {
    val stream = Stream(3, 4, 5, 1, 1, 1).takeWhile(_ != 1)
    (slurp(stream) must_=== List(3, 4, 5)) and (slurpM(stream) must_=== List(3, 4, 5))
  }

  def map = {
    val stream = Stream(1, 1, 1).map(_ + 1)
    (slurp(stream) must_=== List(2, 2, 2)) and (slurpM(stream) must_=== List(2, 2, 2))
  }

  def mapConcat = {
    val stream = Stream(1, 2, 3).mapConcat(i => Chunk(i, i))
    (slurp(stream) must_=== List(1, 1, 2, 2, 3, 3)) and (slurpM(stream) must_=== List(1, 1, 2, 2, 3, 3))
  }

  def zipWithIndex = {
    val stream = Stream(1, 1, 1).zipWithIndex
    (slurp(stream) must_=== List((1, 0), (1, 1), (1, 2))) and (slurpM(stream) must_=== List((1, 0), (1, 1), (1, 2)))
  }

  def scan = {
    val stream = Stream(1, 1, 1).scan(0)((acc, el) => (acc + el, acc + el))
    (slurp(stream) must_=== List(1, 2, 3)) and (slurpM(stream) must_=== List(1, 2, 3))
  }

  def unfold = {
    val s = Stream.unfold(0)(i => if (i < 10) Some((i, i + 1)) else None)
    slurp(s) must_=== (0 to 9).toList and (slurpM(s) must_=== (0 to 9).toList)
  }

  def unfoldM = {
    val s = Stream.unfoldM(0)(i => if (i < 10) IO.now(Some((i, i + 1))) else IO.now(None))
    slurp(s) must_=== (0 to 9).toList and (slurpM(s) must_=== (0 to 9).toList)
  }

  def range = {
    val s = Stream.range(0, 9)
    slurp(s) must_=== (0 to 9).toList and (slurpM(s) must_=== (0 to 9).toList)
  }

  def take = {
    val s = Stream.range(0, 9).take(3)
    slurp(s) must_=== (0 to 2).toList and (slurpM(s) must_=== (0 to 2).toList)
  }

  def concat = {
    val s = Stream(1, 2, 3) ++ Stream(4, 5, 6)
    slurp(s) must_=== (1 to 6).toList and (slurpM(s) must_=== (1 to 6).toList)
  }

  def foreach0 = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1, 1)

    unsafeRun(
      s.foreach0(
        a =>
          IO.sync(
            if (sum >= 3) false
            else {
              sum += a; true
            }
          )
      )
    )
    sum must_=== 3
  }

  def foreach = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1)

    unsafeRun(s.foreach(a => IO.sync(sum += a)))
    sum must_=== 5
  }

  def collect = {
    val s = Stream(Left(1), Right(2), Left(3)).collect {
      case Right(n) => n
    }

    slurp(s) must_=== List(2) and (slurpM(s) must_=== List(2))
  }

  def flatMap = {
    val s = Stream(1, 1).flatMap(a => Stream(a, a))

    slurp(s) must_=== List(1, 1, 1, 1) and (slurpM(s) must_=== List(1, 1, 1, 1))
  }

  def forever = {
    var sum = 0
    val s   = Stream(1).forever.foreach0(a => IO.sync { sum += a; if (sum >= 9) false else true })

    unsafeRun(s)
    sum must_=== 9
  }

  def joinWith = {
    val s1 = Stream(1, 1)
    val s2 = Stream(2, 2)
    val join = s1.joinWith(s2, 1, 1)(
      (a: IO[Nothing, Option[Int]], b: IO[Nothing, Option[Int]]) =>
        a.seqWith(b)((a, b) => a flatMap (a => b map ((a, _))))
    )

    slurp(join) must_=== List((1, 2), (1, 2)) and (slurpM(join) must_=== List((1, 2), (1, 2)))
  }

  def merge = {
    val s1 = Stream(Left(1), Left(2))
    val s2 = Stream(Right(1), Right(2))

    val merge = s1.merge(s2)

    slurpM(merge) must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  def mergeEither = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeEither(s2)

    slurpM(merge) must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  def mergeWith = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)

    slurpM(merge) must containTheSameElementsAs(List("1", "2", "1", "2"))
  }
}
