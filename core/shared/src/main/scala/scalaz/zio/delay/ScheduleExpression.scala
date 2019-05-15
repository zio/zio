package scalaz.zio.delay

import java.time.temporal.ChronoField
import java.time.{ LocalDate, LocalTime }

sealed abstract class ScheduleExpression {

  def delay: Long

}

object ScheduleExpression {

  private[delay] sealed class WeekDayExpression(weekDay: Int) extends ScheduleExpression {
    override def delay: Long = {
      val currentDay = LocalDate.now().getDayOfWeek.getValue

      val plusDays =
        if (weekDay > currentDay)
          currentDay - weekDay
        else
          7 - Math.abs(currentDay - weekDay)

      LocalDate.now().plusDays(plusDays).toEpochDay * 24 * 60 * 60 * 1000
    }
  }

  case object Monday    extends WeekDayExpression(1)
  case object Tuesday   extends WeekDayExpression(2)
  case object Wednesday extends WeekDayExpression(3)
  case object Thursday  extends WeekDayExpression(4)
  case object Friday    extends WeekDayExpression(5)
  case object Saturday  extends WeekDayExpression(6)
  case object Sunday    extends WeekDayExpression(7)

  sealed case class EveryDay(hour: Int, minute: Int) extends ScheduleExpression {
    override def delay: Long = {
      val today        = LocalDate.now()
      val scheduleTime = LocalTime.of(hour, minute)
      val time         = LocalTime.now()

      val scheduleMillis = today.toEpochDay * 24 * 60 * 60 * 1000 + scheduleTime.toNanoOfDay / 1000000

      if (time.get(ChronoField.HOUR_OF_DAY) <= hour && time.get(ChronoField.MINUTE_OF_HOUR) <= minute)
        scheduleMillis
      else
        scheduleMillis + 86400000
    }
  }
}
