package zio.blocks.schema

sealed trait PrimitiveValue {
  type Type

  def primitiveType: PrimitiveType[Type]
}
object PrimitiveValue {
  sealed trait Val extends PrimitiveValue {
    type Type <: AnyVal
  }
  sealed trait Ref extends PrimitiveValue {
    type Type <: AnyRef
  }
  case object Unit extends Val {
    final type Type = scala.Unit

    def primitiveType: PrimitiveType[scala.Unit] = PrimitiveType.Unit
  }
  final case class Boolean(value: scala.Boolean) extends Val {
    final type Type = scala.Boolean

    def primitiveType: PrimitiveType[scala.Boolean] = PrimitiveType.Boolean(Validation.None)
  }
  final case class Byte(value: scala.Byte) extends Val {
    final type Type = scala.Byte

    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)
  }
  final case class Short(value: scala.Short) extends Val {
    final type Type = scala.Short

    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)
  }
  final case class Int(value: scala.Int) extends Val {
    final type Type = scala.Int

    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)
  }
  final case class Long(value: scala.Long) extends Val {
    final type Type = scala.Long

    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)
  }
  final case class Float(value: scala.Float) extends Val {
    final type Type = scala.Float

    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)
  }
  final case class Double(value: scala.Double) extends Val {
    final type Type = scala.Double

    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)
  }
  final case class Char(value: scala.Char) extends Val {
    final type Type = scala.Char

    def primitiveType: PrimitiveType[scala.Char] = PrimitiveType.Char(Validation.None)
  }
  final case class String(value: Predef.String) extends Ref {
    final type Type = Predef.String

    def primitiveType: PrimitiveType[Predef.String] = PrimitiveType.String(Validation.None)
  }
  final case class BigInt(value: scala.BigInt) extends Ref {
    final type Type = scala.BigInt

    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)
  }
  final case class BigDecimal(value: scala.BigDecimal) extends Ref {
    final type Type = scala.BigDecimal

    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)
  }
  final case class DayOfWeek(value: java.time.DayOfWeek) extends Ref {
    final type Type = java.time.DayOfWeek

    def primitiveType: PrimitiveType[java.time.DayOfWeek] = PrimitiveType.DayOfWeek(Validation.None)
  }
  final case class Duration(value: java.time.Duration) extends Ref {
    final type Type = java.time.Duration

    def primitiveType: PrimitiveType[java.time.Duration] = PrimitiveType.Duration(Validation.None)
  }
  final case class Instant(value: java.time.Instant) extends Ref {
    final type Type = java.time.Instant

    def primitiveType: PrimitiveType[java.time.Instant] = PrimitiveType.Instant(Validation.None)
  }
  final case class LocalDate(value: java.time.LocalDate) extends Ref {
    final type Type = java.time.LocalDate

    def primitiveType: PrimitiveType[java.time.LocalDate] = PrimitiveType.LocalDate(Validation.None)
  }
  final case class LocalDateTime(value: java.time.LocalDateTime) extends Ref {
    final type Type = java.time.LocalDateTime

    def primitiveType: PrimitiveType[java.time.LocalDateTime] = PrimitiveType.LocalDateTime(Validation.None)
  }
  final case class LocalTime(value: java.time.LocalTime) extends Ref {
    final type Type = java.time.LocalTime

    def primitiveType: PrimitiveType[java.time.LocalTime] = PrimitiveType.LocalTime(Validation.None)
  }
  final case class Month(value: java.time.Month) extends Ref {
    final type Type = java.time.Month

    def primitiveType: PrimitiveType[java.time.Month] = PrimitiveType.Month(Validation.None)
  }
  final case class MonthDay(value: java.time.MonthDay) extends Ref {
    final type Type = java.time.MonthDay

    def primitiveType: PrimitiveType[java.time.MonthDay] = PrimitiveType.MonthDay(Validation.None)
  }
  final case class OffsetDateTime(value: java.time.OffsetDateTime) extends Ref {
    final type Type = java.time.OffsetDateTime

    def primitiveType: PrimitiveType[java.time.OffsetDateTime] = PrimitiveType.OffsetDateTime(Validation.None)
  }
  final case class OffsetTime(value: java.time.OffsetTime) extends Ref {
    final type Type = java.time.OffsetTime

    def primitiveType: PrimitiveType[java.time.OffsetTime] = PrimitiveType.OffsetTime(Validation.None)
  }
  final case class Period(value: java.time.Period) extends Ref {
    final type Type = java.time.Period

    def primitiveType: PrimitiveType[java.time.Period] = PrimitiveType.Period(Validation.None)
  }
  final case class Year(value: java.time.Year) extends Ref {
    final type Type = java.time.Year

    def primitiveType: PrimitiveType[java.time.Year] = PrimitiveType.Year(Validation.None)
  }
  final case class YearMonth(value: java.time.YearMonth) extends Ref {
    final type Type = java.time.YearMonth

    def primitiveType: PrimitiveType[java.time.YearMonth] = PrimitiveType.YearMonth(Validation.None)
  }
  final case class ZoneId(value: java.time.ZoneId) extends Ref {
    final type Type = java.time.ZoneId

    def primitiveType: PrimitiveType[java.time.ZoneId] = PrimitiveType.ZoneId(Validation.None)
  }
  final case class ZoneOffset(value: java.time.ZoneOffset) extends Ref {
    final type Type = java.time.ZoneOffset

    def primitiveType: PrimitiveType[java.time.ZoneOffset] = PrimitiveType.ZoneOffset(Validation.None)
  }
  final case class ZonedDateTime(value: java.time.ZonedDateTime) extends Ref {
    final type Type = java.time.ZonedDateTime

    def primitiveType: PrimitiveType[java.time.ZonedDateTime] = PrimitiveType.ZonedDateTime(Validation.None)
  }
  final case class Currency(value: java.util.Currency) extends Ref {
    final type Type = java.util.Currency

    def primitiveType: PrimitiveType[java.util.Currency] = PrimitiveType.Currency(Validation.None)
  }
  final case class UUID(value: java.util.UUID) extends Ref {
    final type Type = java.util.UUID

    def primitiveType: PrimitiveType[java.util.UUID] = PrimitiveType.UUID(Validation.None)
  }
}
