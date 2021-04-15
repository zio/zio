import java.time.{Duration => JavaDuration}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration => ScalaFiniteDuration}
import scala.language.implicitConversions
import scala.math.Ordering

/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package object zio
    extends BuildFromCompat
    with EitherCompat
    with FunctionToLayerOps
    with IntersectionTypeCompat
    with PlatformSpecific
    with VersionSpecific {
  private[zio] type Callback[E, A] = Exit[E, A] => Any

  type Canceler[-R] = URIO[R, Any]

  type IO[+E, +A]   = ZIO[Any, E, A]         // Succeed with an `A`, may fail with `E`        , no requirements.
  type Task[+A]     = ZIO[Any, Throwable, A] // Succeed with an `A`, may fail with `Throwable`, no requirements.
  type RIO[-R, +A]  = ZIO[R, Throwable, A]   // Succeed with an `A`, may fail with `Throwable`, requires an `R`.
  type UIO[+A]      = ZIO[Any, Nothing, A]   // Succeed with an `A`, cannot fail              , no requirements.
  type URIO[-R, +A] = ZIO[R, Nothing, A]     // Succeed with an `A`, cannot fail              , requires an `R`.

  type Managed[+E, +A]   = ZManaged[Any, E, A]         //Manage an `A`, may fail with `E`        , no requirements
  type TaskManaged[+A]   = ZManaged[Any, Throwable, A] //Manage an `A`, may fail with `Throwable`, no requirements
  type RManaged[-R, +A]  = ZManaged[R, Throwable, A]   //Manage an `A`, may fail with `Throwable`, requires an `R`
  type UManaged[+A]      = ZManaged[Any, Nothing, A]   //Manage an `A`, cannot fail              , no requirements
  type URManaged[-R, +A] = ZManaged[R, Nothing, A]     //Manage an `A`, cannot fail              , requires an `R`

  val Managed: ZManaged.type = ZManaged

  type RLayer[-RIn, +ROut]  = ZLayer[RIn, Throwable, ROut]
  type URLayer[-RIn, +ROut] = ZLayer[RIn, Nothing, ROut]
  type Layer[+E, +ROut]     = ZLayer[Any, E, ROut]
  type ULayer[+ROut]        = ZLayer[Any, Nothing, ROut]
  type TaskLayer[+ROut]     = ZLayer[Any, Throwable, ROut]

  type Queue[A] = ZQueue[Any, Any, Nothing, Nothing, A, A]

  /**
   * A queue that can only be dequeued.
   */
  type ZDequeue[-R, +E, +A] = ZQueue[Nothing, R, Any, E, Nothing, A]
  type Dequeue[+A]          = ZQueue[Nothing, Any, Any, Nothing, Nothing, A]

  /**
   * A queue that can only be enqueued.
   */
  type ZEnqueue[-R, +E, -A] = ZQueue[R, Nothing, E, Any, A, Any]
  type Enqueue[-A]          = ZQueue[Any, Nothing, Nothing, Any, A, Any]

  type Ref[A]      = ZRef[Any, Any, Nothing, Nothing, A, A]
  type ERef[+E, A] = ZRef[Any, Any, E, E, A, A]

  type ZRefM[-RA, -RB, +EA, +EB, -A, +B] = ZRef.ZRefM[RA, RB, EA, EB, A, B]
  type RefM[A]                           = ZRefM[Any, Any, Nothing, Nothing, A, A]
  type ERefM[+E, A]                      = ZRefM[Any, Any, E, E, A, A]

  type Hub[A] = ZHub[Any, Any, Nothing, Nothing, A, A]
  val Hub: ZHub.type = ZHub

  type Semaphore = stm.TSemaphore

  object <*> {
    def unapply[A, B](ab: (A, B)): Some[(A, B)] =
      Some((ab._1, ab._2))
  }

  // Duration

  type Duration = java.time.Duration

  implicit def durationInt(n: Int): DurationSyntax = new DurationSyntax(n.toLong)

  implicit def durationLong(n: Long): DurationSyntax = new DurationSyntax(n)

  implicit final class DurationOps(private val duration: Duration) extends AnyVal {

    def +(other: Duration): Duration = {
      val thisNanos  = if (duration.toNanos > 0) duration.toNanos else 0
      val otherNanos = if (other.toNanos > 0) other.toNanos else 0
      val sum        = thisNanos + otherNanos
      if (sum >= 0) sum.nanos else Duration.Infinity
    }

    def *(factor: Double): Duration = {
      val nanos = duration.toNanos
      if (factor <= 0 || nanos <= 0) Duration.Zero
      else if (factor <= Long.MaxValue / nanos.toDouble) (nanos * factor).round.nanoseconds
      else Duration.Infinity
    }

    def >=(other: Duration): Boolean = duration.compareTo(other) >= 0
    def <=(other: Duration): Boolean = duration.compareTo(other) <= 0
    def >(other: Duration): Boolean  = duration.compareTo(other) > 0
    def <(other: Duration): Boolean  = duration.compareTo(other) < 0
    def ==(other: Duration): Boolean = duration.compareTo(other) == 0

    def render: String = {
      val nanos = duration.toNanos
      TimeUnit.NANOSECONDS.toMillis(nanos) match {
        case 0                       => s"$nanos ns"
        case millis if millis < 1000 => s"$millis ms"
        case millis if millis < 60000 =>
          val maybeMs = Option(millis % 1000).filterNot(_ == 0)
          s"${millis / 1000} s${maybeMs.fold("")(ms => s" $ms ms")}"
        case millis if millis < 3600000 =>
          val maybeSec = Option((millis % 60000) / 1000).filterNot(_ == 0)
          s"${millis / 60000} m${maybeSec.fold("")(s => s" $s s")}"
        case millis =>
          val days    = millis / 86400000
          val hours   = (millis % 86400000) / 3600000
          val minutes = (millis % 3600000) / 60000
          val seconds = (millis % 60000) / 1000

          List(days, hours, minutes, seconds)
            .zip(List("d", "h", "m", "s"))
            .collect { case (value, unit) if value != 0 => s"$value $unit" }
            .mkString(" ")
      }
    }

    def asScala: ScalaDuration = duration match {
      case Duration.Infinity => ScalaDuration.Inf
      case _                 => ScalaFiniteDuration(duration.toNanos, TimeUnit.NANOSECONDS)
    }

    def asJava: JavaDuration = duration match {
      case Duration.Infinity => JavaDuration.ofNanos(Long.MaxValue)
      case _                 => JavaDuration.ofNanos(duration.toNanos)
    }

    def max(other: Duration): Duration = if (duration > other) duration else other

    def min(other: Duration): Duration = if (duration < other) duration else other

    def compare(other: Duration): Int = duration.toNanos compare other.toNanos

    def fold[Z](infinity: => Z, finite: Duration => Z): Z = duration match {
      case Duration.Infinity => infinity
      case f: Duration       => finite(f)
    }

  }

  implicit val durationOrdering: Ordering[Duration] = new Ordering[Duration] {
    override def compare(x: Duration, y: Duration): Int = x.compareTo(y)
  }

}
