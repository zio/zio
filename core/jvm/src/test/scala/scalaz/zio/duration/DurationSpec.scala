package scalaz.zio.duration

import java.time.{ Duration => JavaDuration }
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration }

import scalaz.zio.TestRuntime

class DurationSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "DurationSpec".title ^ s2"""
        Make a Duration from positive nanos and check that:
          The Duration is Finite                                               $pos1
          Copy with a negative nanos returns Zero                              $pos2
          Multiplying with a negative factor returns Zero                      $pos3
          Its stdlib representation is correct                                 $pos4
          Its JDK representation is correct                                    $pos5
          It identifies as "zero"                                              $pos6
          Creating it with a j.u.c.TimeUnit is identical                       $pos7
          It knows its length in ns                                            $pos8
          It knows its length in ms                                            $pos9
          max(1 ns, 2 ns) is 2 ns                                              $pos10
          min(1 ns, 2 ns) is 1 ns                                              $pos11
          max(2 ns, 1 ns) is 2 ns                                              $pos12
          min(2 ns, 1 ns) is 1 ns                                              $pos13
          10 ns + 20 ns = 30 ns                                                $pos14
          10 ns * NaN = Infinity                                               $pos15
          10 ns compared to Infinity is -1                                     $pos16
          10 ns compared to 10 ns is 0                                         $pos17
          + with positive overflow results in Infinity                         $pos18
          * with negative duration results in zero                             $pos19
          * with overflow to positive results in Infinity                      $pos20
          * with overflow to negative results in Infinity                      $pos21
          Folding picks up the correct value                                   $pos22

        Make a Duration from negative nanos and check that:
          The Duration is Zero                                                 $neg1

        Take Infinity and check that:
          It returns -1 milliseconds                                           $inf1
          It returns -1 nanoseconds                                            $inf2
          Infinity + Infinity = Infinity                                       $inf3
          Infinity + 1 ns = Infinity                                           $inf4
          1 ns + Infinity = Infinity                                           $inf5
          Infinity * 10 = Infinity                                             $inf6
          Infinity compared to Infinity is 0                                   $inf7
          Infinity compared to 1 ns is 1                                       $inf8
          Infinity is not zero                                                 $inf9
          It converts into the infinite s.c.d.Duration                         $inf10
          It converts into a Long.MaxValue second-long JDK Duration            $inf11
          Folding picks up the correct value                                   $inf12
          Infinity * -10 = Zero                                                $inf13

        Make a Scala stdlib s.c.d.Duration and check that:
          A negative s.c.d.Duration converts to Zero                           $dur1
          The infinite s.c.d.Duration converts to Infinity                     $dur2
          A positive s.c.d.Duration converts to a Finite                       $dur3
     """

  def pos1 =
    Duration.fromNanos(1) must haveClass[Duration.Finite]

  def pos2 =
    Duration.fromNanos(1).asInstanceOf[Duration.Finite].copy(-1) must_=== Duration.Zero

  def pos3 =
    Duration.fromNanos(1) * -1.0 must_=== Duration.Zero

  def pos4 =
    Duration.fromNanos(1234L).asScala must_=== ScalaFiniteDuration(1234L, TimeUnit.NANOSECONDS)

  def pos5 =
    Duration.fromNanos(2345L).asJava must_=== JavaDuration.ofNanos(2345L)

  def pos6 =
    Duration.fromNanos(0L).isZero must_=== true

  def pos7 =
    Duration(12L, TimeUnit.NANOSECONDS) must_=== Duration.fromNanos(12L)

  def pos8 =
    Duration.fromNanos(123L).toNanos must_=== 123L

  def pos9 =
    Duration.fromNanos(123000000L).toMillis must_=== 123L

  def pos10 =
    Duration.fromNanos(1L).max(Duration.fromNanos(2L)) must_=== Duration.fromNanos(2L)

  def pos11 =
    Duration.fromNanos(1L).min(Duration.fromNanos(2L)) must_=== Duration.fromNanos(1L)

  def pos12 =
    Duration.fromNanos(2L).max(Duration.fromNanos(1L)) must_=== Duration.fromNanos(2L)

  def pos13 =
    Duration.fromNanos(2L).min(Duration.fromNanos(1L)) must_=== Duration.fromNanos(1L)

  def pos14 =
    Duration.fromNanos(10L) + Duration.fromNanos(20L) must_=== Duration.fromNanos(30L)

  def pos15 =
    Duration.fromNanos(10L) * Double.NaN must_=== Duration.Infinity

  def pos16 =
    Duration.fromNanos(10L) compare Duration.Infinity must_=== -1

  def pos17 =
    Duration.fromNanos(10L) compare Duration.fromNanos(10L) must_=== 0

  def pos18 =
    Duration.fromNanos(Long.MaxValue - 1) + Duration.fromNanos(42) must_=== Duration.Infinity

  def pos19 =
    Duration.fromNanos(42) * -7 must_=== Duration.Zero

  def pos20 =
    Duration.fromNanos(Long.MaxValue) * 3 must_=== Duration.Infinity

  def pos21 =
    Duration.fromNanos(Long.MaxValue) * 2 must_=== Duration.Infinity

  def pos22 =
    Duration.fromNanos(Long.MaxValue).fold("Infinity", _ => "Finite") must_=== "Finite"

  def neg1 =
    Duration.fromNanos(-1) must_=== Duration.Zero

  def inf1 =
    Duration.Infinity.toMillis must_=== Long.MaxValue / 1000000

  def inf2 =
    Duration.Infinity.toNanos must_=== Long.MaxValue

  def inf3 =
    Duration.Infinity + Duration.Infinity must_=== Duration.Infinity

  def inf4 =
    Duration.Infinity + Duration.fromNanos(1L) must_=== Duration.Infinity

  def inf5 =
    Duration.fromNanos(1L) + Duration.Infinity must_=== Duration.Infinity

  def inf6 =
    Duration.Infinity * 10.0 must_=== Duration.Infinity

  def inf7 =
    Duration.Infinity compare Duration.Infinity must_=== 0

  def inf8 =
    Duration.Infinity compare Duration.fromNanos(1L) must_=== 1

  def inf9 =
    Duration.Infinity.isZero must_=== false

  def inf10 =
    Duration.Infinity.asScala must_=== ScalaDuration.Inf

  def inf11 =
    Duration.Infinity.asJava must_=== JavaDuration.ofSeconds(Long.MaxValue)

  def inf12 =
    Duration.Infinity.fold("Infinity", _ => "Finite") must_=== "Infinity"

  def inf13 =
    Duration.Infinity * -10 must_=== Duration.Zero

  def dur1 =
    Duration.fromScala(ScalaDuration(-1L, TimeUnit.NANOSECONDS)) must_=== Duration.Zero

  def dur2 =
    Duration.fromScala(ScalaDuration.Inf) must_=== Duration.Infinity

  def dur3 =
    Duration.fromScala(ScalaDuration(1L, TimeUnit.NANOSECONDS)) must_=== Duration.fromNanos(1L)
}
