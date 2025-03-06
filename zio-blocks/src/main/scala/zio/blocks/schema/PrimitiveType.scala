package zio.blocks.schema

// FIXME: Add all primitive types, including date/time, currency, etc.
sealed trait PrimitiveType[A] {
  def validation: Validation[A]
}
object PrimitiveType {
  sealed trait Val[A <: AnyVal] extends PrimitiveType[A]
  sealed trait Ref[A <: AnyRef] extends PrimitiveType[A]

  case object Unit extends Val[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None
  }
  final case class Boolean(
    validation: Validation[scala.Boolean]
  ) extends Val[scala.Boolean]
  final case class Byte(
    validation: Validation[scala.Byte]
  ) extends Val[scala.Byte]
  final case class Short(
    validation: Validation[scala.Short]
  ) extends Val[scala.Short]
  final case class Int(
    validation: Validation[scala.Int]
  ) extends Val[scala.Int]
  final case class Long(
    validation: Validation[scala.Long]
  ) extends Val[scala.Long]
  final case class Float(
    validation: Validation[scala.Float]
  ) extends Val[scala.Float]
  final case class Double(
    validation: Validation[scala.Double]
  ) extends Val[scala.Double]
  final case class Char(
    validation: Validation[scala.Char]
  ) extends Val[scala.Char]
  final case class String(
    validation: Validation[Predef.String]
  ) extends Ref[Predef.String]
  final case class BigInt(
    validation: Validation[scala.BigInt]
  ) extends Ref[scala.BigInt]
  final case class BigDecimal(
    validation: Validation[scala.BigDecimal]
  ) extends Ref[scala.BigDecimal]
  final case class DayOfWeek(
    validation: Validation[java.time.DayOfWeek]
  ) extends Ref[java.time.DayOfWeek]
  final case class Duration(
    validation: Validation[java.time.Duration]
  ) extends Ref[java.time.Duration]
  final case class Instant(
    validation: Validation[java.time.Instant]
  ) extends Ref[java.time.Instant]
  final case class LocalDate(
    validation: Validation[java.time.LocalDate]
  ) extends Ref[java.time.LocalDate]
  final case class LocalDateTime(
    validation: Validation[java.time.LocalDateTime]
  ) extends Ref[java.time.LocalDateTime]
  final case class LocalTime(
    validation: Validation[java.time.LocalTime]
  ) extends Ref[java.time.LocalTime]
  final case class Month(
    validation: Validation[java.time.Month]
  ) extends Ref[java.time.Month]
  final case class MonthDay(
    validation: Validation[java.time.MonthDay]
  ) extends Ref[java.time.MonthDay]
  final case class OffsetDateTime(
    validation: Validation[java.time.OffsetDateTime]
  ) extends Ref[java.time.OffsetDateTime]
  final case class OffsetTime(
    validation: Validation[java.time.OffsetTime]
  ) extends Ref[java.time.OffsetTime]
  final case class Period(
    validation: Validation[java.time.Period]
  ) extends Ref[java.time.Period]
  final case class Year(
    validation: Validation[java.time.Year]
  ) extends Ref[java.time.Year]
  final case class YearMonth(
    validation: Validation[java.time.YearMonth]
  ) extends Ref[java.time.YearMonth]
  final case class ZoneId(
    validation: Validation[java.time.ZoneId]
  ) extends Ref[java.time.ZoneId]
  final case class ZoneOffset(
    validation: Validation[java.time.ZoneOffset]
  ) extends Ref[java.time.ZoneOffset]
  final case class ZonedDateTime(
    validation: Validation[java.time.ZonedDateTime]
  ) extends Ref[java.time.ZonedDateTime]

}
