package zio.duration

import java.time.{ Duration => JavaDuration }
import java.util.concurrent.TimeUnit

import org.specs2.Specification

import scala.concurrent.duration.{ Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration }

class DurationSpec extends Specification {

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

        Make a Java stdlib j.t.Duration and check that:
          A negative j.t.Duration converts to Zero                                $jdur1
          A Long.MaxValue second j.t.Duration converts to Infinity                $jdur2
          A nano-adjusted Long.MaxValue second j.t.Duration converts to Infinity  $jdur3
          A j.t.Duration constructed from Infinity converts to Infinity           $jdur4
          A Long.MaxValue - 1 second j.t.Duration converts to Infinity            $jdur5
          A +ve j.t.Duration whose nano conversion overflows converts to Infinity $jdur6
          A -ve j.t.Duration whose nano conversion overflows converts to Zero     $jdur7
          A positive j.t.Duration converts to a Finite                            $jdur8

        Check multiplication with finite durations:
          Zero multiplied with zero                                            $mul0
          Zero multiplied with one                                             $mul1
          One second multiplied with 60                                        $mul2

        Render duration:
          0 ms         $render0ms
          1 ms         $render1ms
          1 s          $render1s
          2 s 500 ms   $render2s500ms
          1 min        $render1min
          2 min 30 s   $render2min30

        Long:

          1L.nano         = fromNanos(1L)                          $nano1L
          2L.nanos        = fromNanos(2L)                          $nano2L
          1L.nanosecond   = fromNanos(1L)                          $nano3L
          2L.nanoseconds  = fromNanos(2L)                          $nano4L
          1L.micro        = fromNanos(1000L)                       $nano5L
          2L.micros       = fromNanos(2000L)                       $nano6L
          1L.microsecond  = fromNanos(1000L)                       $nano7L
          2L.microseconds = fromNanos(2000L)                       $nano8L
          1L.milli        = fromNanos(1000000L)                    $nano9L
          2L.millis       = fromNanos(2000000L)                    $nano10L
          1L.millisecond  = fromNanos(1000000L)                    $nano11L
          2L.milliseconds = fromNanos(2000000L)                    $nano12L
          1L.second       = fromNanos(1000000000L)                 $nano13L
          2L.seconds      = fromNanos(2000000000L)                 $nano14L
          1L.minute       = fromNanos(60000000000L)                $nano15L
          2L.minutes      = fromNanos(120000000000L)               $nano16L
          1L.hour         = fromNanos(3600000000000L)              $nano17L
          2L.hours        = fromNanos(7200000000000L)              $nano18L
          1L.day          = fromNanos(86400000000000L)             $nano19L
          2L.days         = fromNanos(172800000000000L)            $nano20L

        Int:

          1.nano         = fromNanos(1L)                         $nano1
          2.nanos        = fromNanos(2L)                         $nano2
          1.nanosecond   = fromNanos(1L)                         $nano3
          2.nanoseconds  = fromNanos(2L)                         $nano4
          1.micro        = fromNanos(1000L)                      $nano5
          2.micros       = fromNanos(2000L)                      $nano6
          1.microsecond  = fromNanos(1000L)                      $nano7
          2.microseconds = fromNanos(2000L)                      $nano8
          1.milli        = fromNanos(1000000L)                   $nano9
          2.millis       = fromNanos(2000000L)                   $nano10
          1.millisecond  = fromNanos(1000000L)                   $nano11
          2.milliseconds = fromNanos(2000000L)                   $nano12
          1.second       = fromNanos(1000000000L)                $nano13
          2.seconds      = fromNanos(2000000000L)                $nano14
          1.minute       = fromNanos(60000000000L)               $nano15
          2.minutes      = fromNanos(120000000000L)              $nano16
          1.hour         = fromNanos(3600000000000L)             $nano17
          2.hours        = fromNanos(7200000000000L)             $nano18
          1.day          = fromNanos(86400000000000L)            $nano19
          2.days         = fromNanos(76800000000000L)            $nano20
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

  def jdur1 =
    Duration.fromJava(JavaDuration.ofNanos(-1L)) must_=== Duration.Zero

  def jdur2 =
    Duration.fromJava(JavaDuration.ofSeconds(Long.MaxValue)) must_=== Duration.Infinity

  def jdur3 =
    Duration.fromJava(JavaDuration.ofSeconds(Long.MaxValue, 1L)) must_=== Duration.Infinity

  def jdur4 =
    Duration.fromJava(Duration.Infinity.asJava) must_=== Duration.Infinity

  def jdur5 =
    Duration.fromJava(JavaDuration.ofSeconds(Long.MaxValue - 1)) must_=== Duration.Infinity

  def jdur6 =
    Duration.fromJava(JavaDuration.ofNanos(Long.MaxValue).plus(JavaDuration.ofNanos(1L))) must_=== Duration.Infinity

  def jdur7 =
    Duration.fromJava(JavaDuration.ofNanos(Long.MinValue).minus(JavaDuration.ofNanos(1L))) must_=== Duration.Zero

  def jdur8 =
    Duration.fromJava(JavaDuration.ofNanos(1L)) must_=== Duration.fromNanos(1L)

  def mul0 =
    Duration.Zero * 0 must_=== Duration.Zero

  def mul1 =
    Duration.Zero * 1 must_=== Duration.Zero

  def mul2 =
    Duration(1, TimeUnit.SECONDS) * 60 must_=== Duration(1, TimeUnit.MINUTES)

  def render0ms =
    Duration(0, TimeUnit.MILLISECONDS).render must_=== "0 ms"

  def render1ms =
    Duration(1, TimeUnit.MILLISECONDS).render must_=== "1 ms"

  def render1s =
    Duration(1, TimeUnit.SECONDS).render must_=== "1 s"

  def render2s500ms =
    Duration(2500, TimeUnit.MILLISECONDS).render must_=== "2 s 500 ms"

  def render1min =
    Duration(1, TimeUnit.MINUTES).render must_=== "1 m"

  def render2min30 =
    Duration(150, TimeUnit.SECONDS).render must_=== "2 m 30 s"

  def nano1L =
    1L.nano must_=== Duration.fromNanos(1L)

  def nano2L =
    2L.nanos must_=== Duration.fromNanos(2L)

  def nano3L =
    1L.nanosecond must_=== Duration.fromNanos(1L)

  def nano4L =
    2L.nanoseconds must_=== Duration.fromNanos(2L)

  def nano5L =
    1L.micro must_=== Duration.fromNanos(1000L)

  def nano6L =
    2L.micros must_=== Duration.fromNanos(2000L)

  def nano7L =
    1L.microsecond must_=== Duration.fromNanos(1000L)

  def nano8L =
    2L.microseconds must_=== Duration.fromNanos(2000L)

  def nano9L =
    1L.milli must_=== Duration.fromNanos(1000000L)

  def nano10L =
    2L.millis must_=== Duration.fromNanos(2000000L)

  def nano11L =
    1L.millisecond must_=== Duration.fromNanos(1000000L)

  def nano12L =
    2L.milliseconds must_=== Duration.fromNanos(2000000L)

  def nano13L =
    1L.second must_=== Duration.fromNanos(1000000000L)

  def nano14L =
    2L.seconds must_=== Duration.fromNanos(2000000000L)

  def nano15L =
    1L.minute must_=== Duration.fromNanos(60000000000L)

  def nano16L =
    2L.minutes must_=== Duration.fromNanos(120000000000L)

  def nano17L =
    1L.hour must_=== Duration.fromNanos(3600000000000L)

  def nano18L =
    2L.hours must_=== Duration.fromNanos(7200000000000L)

  def nano19L =
    1L.day must_=== Duration.fromNanos(86400000000000L)

  def nano20L =
    2L.days must_=== Duration.fromNanos(172800000000000L)

  def nano1 =
    1.nano must_=== Duration.fromNanos(1L)

  def nano2 =
    2.nanos must_=== Duration.fromNanos(2L)

  def nano3 =
    1.nanosecond must_=== Duration.fromNanos(1L)

  def nano4 =
    2.nanos must_=== Duration.fromNanos(2L)

  def nano5 =
    1.micro must_=== Duration.fromNanos(1000L)

  def nano6 =
    2.micros must_=== Duration.fromNanos(2000L)

  def nano7 =
    1.microsecond must_=== Duration.fromNanos(1000L)

  def nano8 =
    2.microseconds must_=== Duration.fromNanos(2000L)

  def nano9 =
    1.milli must_=== Duration.fromNanos(1000000L)

  def nano10 =
    2.millis must_=== Duration.fromNanos(2000000L)

  def nano11 =
    1.millisecond must_=== Duration.fromNanos(1000000L)

  def nano12 =
    2.milliseconds must_=== Duration.fromNanos(2000000L)

  def nano13 =
    1.second must_=== Duration.fromNanos(1000000000L)

  def nano14 =
    2.seconds must_=== Duration.fromNanos(2000000000L)

  def nano15 =
    1.minute must_=== Duration.fromNanos(60000000000L)

  def nano16 =
    2.minutes must_=== Duration.fromNanos(120000000000L)

  def nano17 =
    1.hour must_=== Duration.fromNanos(3600000000000L)

  def nano18 =
    2.hours must_=== Duration.fromNanos(7200000000000L)

  def nano19 =
    1.day must_=== Duration.fromNanos(86400000000000L)

  def nano20 =
    2.days must_=== Duration.fromNanos(172800000000000L)

}
