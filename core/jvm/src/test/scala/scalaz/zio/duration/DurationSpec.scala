package scalaz.zio.duration

import java.time.{ Duration => JavaDuration }
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration }

import scalaz.zio.AbstractRTSSpec

class DurationSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "DurationSpec".title ^ s2"""
        Make a Duration from positive nanos and check that:
          The Duration is Finite                                               $pos1
          Its stdlib representation is correct                                 $pos2
          Its JDK representation is correct                                    $pos3
          Creating it with a j.u.c.TimeUnit is identical                       $pos5
          It knows its length in ns                                            $pos6
          It knows its length in ms                                            $pos7
          max(1 ns, 2 ns) is 2 ns                                              $pos8
          min(1 ns, 2 ns) is 1 ns                                              $pos9
          max(2 ns, 1 ns) is 2 ns                                              $pos10
          min(2 ns, 1 ns) is 1 ns                                              $pos11
          10 ns + 20 ns = 30 ns                                                $pos12
          10 ns * NaN = Infinity                                               $pos13
          10 ns compared to Infinity is -1                                     $pos14
          10 ns compared to 10 ns is 0                                         $pos15
          multiplied by positive does not overflow to negative                 $pos16
          multiplied by negative does not overflow to positive                 $pos17
          multiplied by 0 is Zero                                              $pos18
        Make a Duration from negative nanos and check that:
          The Duration is Finite                                               $neg1
          Its stdlib representation is correct                                 $neg2
          Its JDK representation is correct                                    $neg3
          It identifies as "zero"                                              $neg4
          Creating it with a j.u.c.TimeUnit is identical                       $neg5
          It knows its length in ns                                            $neg6
          It knows its length in ms                                            $neg7
          max(-1 ns, -2 ns) is -1 ns                                           $neg8
          min(-1 ns, -2 ns) is -2 ns                                           $neg9
          max(-2 ns, -1 ns) is -1 ns                                           $neg10
          min(-2 ns, -1 ns) is -2 ns                                           $neg11
          -10 ns + -20 ns = -30 ns                                             $neg12
          -10 ns * NaN = Infinity                                              $neg13
          -10 ns compared to Infinity is -1                                    $neg14
          -10 ns compared to -10 ns is 0                                       $neg15
          added to other does not overflow to Infinity                         $neg16
          multiplied by positive does not overflow to positive                 $neg17
          multiplied by negative does not overflow to negative                 $neg18
          multiplied by 0 is Zero                                              $neg19
          multiplied by NegativeInfinity is Infinity                           $neg20
          multiplied by PositiveInfinity is NegativeInfinity                   $neg21

        Take Infinity and check that:
          It returns (Long.MaxValue/1000000) milliseconds                      $inf1
          It returns Long.MaxValue nanoseconds                                 $inf2
          Infinity + Infinity = Infinity                                       $inf3
          Infinity + 1 ns = Infinity                                           $inf4
          1 ns + Infinity = Infinity                                           $inf5
          Infinity * 10 = Infinity                                             $inf6
          Infinity compared to Infinity is 0                                   $inf7
          Infinity compared to 1 ns is 1                                       $inf8
          Infinity is not zero                                                 $inf9
          It converts into the infinite s.c.d.Duration                         $inf10
          It converts into a Long.MaxValue second-long JDK Duration            $inf11

        Duration.Zero
          isZero returns true                                                  $zero1
          equals Duration.fromNanos(0L)                                        $zero2
          * Nan remains Zero                                                   $zero3
          * negative infinity remains Zero                                     $zero4
          * 3 remains Zero                                                     $zero5
          * 0 remains Zero                                                     $zero6

        Make a Scala stdlib s.c.d.Duration and check that:
          A negative s.c.d.Duration converts to a Finite                       $dur1
          The infinite s.c.d.Duration converts to Infinity                     $dur2
          A positive s.c.d.Duration converts to a Finite                       $dur3
     """

  def pos1 =
    Duration.fromNanos(1) must haveClass[Duration.Finite]

  def pos2 =
    Duration.fromNanos(1234L).asScala must_=== ScalaFiniteDuration(1234L, TimeUnit.NANOSECONDS)

  def pos3 =
    Duration.fromNanos(2345L).asJava must_=== JavaDuration.ofNanos(2345L)

  def pos5 =
    Duration(12L, TimeUnit.NANOSECONDS) must_=== Duration.fromNanos(12L)

  def pos6 =
    Duration.fromNanos(123L).toNanos must_=== 123L

  def pos7 =
    Duration.fromNanos(123000000L).toMillis must_=== 123L

  def pos8 =
    Duration.fromNanos(1L).max(Duration.fromNanos(2L)) must_=== Duration.fromNanos(2L)

  def pos9 =
    Duration.fromNanos(1L).min(Duration.fromNanos(2L)) must_=== Duration.fromNanos(1L)

  def pos10 =
    Duration.fromNanos(2L).max(Duration.fromNanos(1L)) must_=== Duration.fromNanos(2L)

  def pos11 =
    Duration.fromNanos(2L).min(Duration.fromNanos(1L)) must_=== Duration.fromNanos(1L)

  def pos12 =
    Duration.fromNanos(10L) + Duration.fromNanos(20L) must_=== Duration.fromNanos(30L)

  def pos13 =
    Duration.fromNanos(10L) * Double.NaN must_=== Duration.Infinity

  def pos14 =
    Duration.fromNanos(10L) compare Duration.Infinity must_=== -1

  def pos15 =
    Duration.fromNanos(10L) compare Duration.fromNanos(10L) must_=== 0

  def pos16 =
    Duration.fromNanos(Long.MaxValue - 1) * 2 must_=== Duration.Infinity

  def pos17 =
    Duration.fromNanos(Long.MaxValue - 1) * -2 must_=== Duration.fromNanos(Long.MinValue)

  def pos18 =
    Duration.fromNanos(42) * 0 must_=== Duration.Zero

  def neg1 =
    Duration.fromNanos(-1) must haveClass[Duration.Finite]

  def neg2 =
    Duration.fromNanos(-1234L).asScala must_=== ScalaFiniteDuration(-1234L, TimeUnit.NANOSECONDS)

  def neg3 =
    Duration.fromNanos(-2345L).asJava must_=== JavaDuration.ofNanos(-2345L)

  def neg4 =
    Duration.fromNanos(-0L).isZero must_=== true

  def neg5 =
    Duration(-12L, TimeUnit.NANOSECONDS) must_=== Duration.fromNanos(-12L)

  def neg6 =
    Duration.fromNanos(-123L).toNanos must_=== -123L

  def neg7 =
    Duration.fromNanos(-123000000L).toMillis must_=== -123L

  def neg8 =
    Duration.fromNanos(-1L).max(Duration.fromNanos(-2L)) must_=== Duration.fromNanos(-1L)

  def neg9 =
    Duration.fromNanos(-1L).min(Duration.fromNanos(-2L)) must_=== Duration.fromNanos(-2L)

  def neg10 =
    Duration.fromNanos(-2L).max(Duration.fromNanos(-1L)) must_=== Duration.fromNanos(-1L)

  def neg11 =
    Duration.fromNanos(-2L).min(Duration.fromNanos(-1L)) must_=== Duration.fromNanos(-2L)

  def neg12 =
    Duration.fromNanos(-10L) + Duration.fromNanos(-20L) must_=== Duration.fromNanos(-30L)

  def neg13 =
    Duration.fromNanos(-10L) * Double.NaN must_=== Duration.Infinity

  def neg14 =
    Duration.fromNanos(-10L) compare Duration.Infinity must_=== -1

  def neg15 =
    Duration.fromNanos(-10L) compare Duration.fromNanos(-10L) must_=== 0

  def neg16 =
    Duration.fromNanos(-42L) + Duration.fromNanos(Long.MinValue) must_=== Duration.fromNanos(Long.MinValue)

  def neg17 =
    Duration.fromNanos(Long.MinValue + 1) * 2 must_=== Duration.fromNanos(Long.MinValue)

  def neg18 =
    Duration.fromNanos(Long.MinValue + 1) * (-2) must_=== Duration.Infinity

  def neg19 =
    Duration.fromNanos(-1000) * 0 must_=== Duration.Zero

  def neg20 =
    Duration.fromNanos(-1000) * Double.NegativeInfinity must_=== Duration.Infinity

  def neg21 =
    Duration.fromNanos(-1000) * Double.PositiveInfinity must_=== Duration.fromNanos(Long.MinValue)

  def inf1 =
    Duration.Infinity.toMillis must_=== Long.MaxValue / 1000000L

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

  def zero1 =
    Duration.fromNanos(0L).isZero must_=== true

  def zero2 =
    Duration.fromNanos(0L) == Duration.Zero must_=== true

  def zero3 =
    Duration.Zero * Double.NaN == Duration.Zero must_=== true

  def zero4 =
    Duration.Zero * Double.NegativeInfinity == Duration.Zero must_=== true

  def zero5 =
    Duration.Zero * 3 == Duration.Zero must_=== true

  def zero6 =
    Duration.Zero * 0 == Duration.Zero must_=== true

  def dur1 =
    Duration.fromScala(ScalaDuration(-1L, TimeUnit.NANOSECONDS)) must_=== Duration.fromNanos(-1L)

  def dur2 =
    Duration.fromScala(ScalaDuration.Inf) must_=== Duration.Infinity

  def dur3 =
    Duration.fromScala(ScalaDuration(1L, TimeUnit.NANOSECONDS)) must_=== Duration.fromNanos(1L)
}
