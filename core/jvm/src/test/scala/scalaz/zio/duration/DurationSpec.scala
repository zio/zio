package scalaz.zio.duration

import java.time.{ Duration => JavaDuration }
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ FiniteDuration => ScalaFiniteDuration }

import scalaz.zio.AbstractRTSSpec

class DurationSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "DurationSpec".title ^ s2"""
        Make a Duration from positive nanos and check that:
          The Duration is Finite                              $pos1
          Copy with a negative nanos returns Infinity         $pos2
          Multiplying with a negative factor returns Infinity $pos3
          Its stdlib representation is correct                $pos4
          Its JDK representation is correct                   $pos5

        Make a Duration from negative nanos and check that:
          The Duration is Infinity                            $neg1
     """

  def pos1 =
    Duration.fromNanos(1) must haveClass[Duration.Finite]

  def pos2 =
    Duration.fromNanos(1).asInstanceOf[Duration.Finite].copy(-1) must_=== Duration.Infinity

  def pos3 =
    Duration.fromNanos(1) * -1.0 must_=== Duration.Infinity

  def pos4 =
    Duration.fromNanos(1234L).asScala must_=== ScalaFiniteDuration(1234L, TimeUnit.NANOSECONDS)

  def pos5 =
    Duration.fromNanos(2345L).asJava must_=== JavaDuration.ofNanos(2345L)

  def neg1 =
    Duration.fromNanos(-1) must_=== Duration.Infinity
}
