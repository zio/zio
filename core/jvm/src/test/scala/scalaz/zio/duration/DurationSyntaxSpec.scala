package scalaz.zio.duration

import scalaz.zio.AbstractRTSSpec

class DurationSyntaxSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec {

  def is = "DurationSyntaxSpec".title ^ s2"""
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
