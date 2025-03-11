package zio.blocks.schema.binding

import RegisterOffset.RegisterOffset

/**
 * A {{Deconstructor}} is a typeclass that can deconstruct a value of type `A`
 * into a set of registers.
 */
abstract class Deconstructor[-A] { self =>
  def usedRegisters: RegisterOffset

  /**
   * Deconstructs a value of type `A` into the registers.
   */
  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit

  /**
   * Transforms this deconstructor into one that operates on a different type --
   * albeit one that can be converted to this type.
   */
  final def contramap[B](f: B => A): Deconstructor[B] =
    new Deconstructor[B] {
      def usedRegisters: RegisterOffset = self.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B): Unit =
        self.deconstruct(out, baseOffset, f(in))
    }

  /**
   * Combines two deconstructors into a single deconstructor that deconstructs a
   * tuple of the two values.
   */
  final def zip[B](that: Deconstructor[B]): Deconstructor[(A, B)] = Deconstructor.tuple2(self, that)
}
object Deconstructor {
  private val _of = new Deconstructor[AnyRef] {
    def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

    def deconstruct(out: Registers, baseOffset: RegisterOffset, in: AnyRef): Unit = out.setObject(baseOffset, 0, in)
  }

  def of[A]: Deconstructor[A] = _of.asInstanceOf[Deconstructor[A]]

  val unit: Deconstructor[Unit] =
    new Deconstructor[Unit] {
      def usedRegisters: RegisterOffset = RegisterOffset.Zero

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Unit): Unit = ()
    }

  val boolean: Deconstructor[Boolean] =
    new Deconstructor[Boolean] {
      def usedRegisters = RegisterOffset(booleans = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Boolean): Unit = out.setBoolean(baseOffset, 0, in)
    }

  val byte: Deconstructor[Byte] =
    new Deconstructor[Byte] {
      def usedRegisters = RegisterOffset(bytes = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Byte): Unit = out.setByte(baseOffset, 0, in)
    }

  val short: Deconstructor[Short] =
    new Deconstructor[Short] {
      def usedRegisters = RegisterOffset(shorts = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Short): Unit = out.setShort(baseOffset, 0, in)
    }

  val int: Deconstructor[Int] =
    new Deconstructor[Int] {
      def usedRegisters = RegisterOffset(ints = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Int): Unit = out.setInt(baseOffset, 0, in)
    }

  val long: Deconstructor[Long] =
    new Deconstructor[Long] {
      def usedRegisters = RegisterOffset(longs = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Long): Unit = out.setLong(baseOffset, 0, in)
    }

  val float: Deconstructor[Float] =
    new Deconstructor[Float] {
      def usedRegisters = RegisterOffset(floats = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Float): Unit = out.setFloat(baseOffset, 0, in)
    }

  val double: Deconstructor[Double] =
    new Deconstructor[Double] {
      def usedRegisters = RegisterOffset(doubles = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Double): Unit = out.setDouble(baseOffset, 0, in)
    }

  val char: Deconstructor[Char] =
    new Deconstructor[Char] {
      def usedRegisters = RegisterOffset(chars = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Char): Unit = out.setChar(baseOffset, 0, in)
    }

  val string: Deconstructor[String] =
    new Deconstructor[String] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: String): Unit = out.setObject(baseOffset, 0, in)
    }

  val bigInt: Deconstructor[BigInt] =
    new Deconstructor[BigInt] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: BigInt): Unit = out.setObject(baseOffset, 0, in)
    }

  val bigDecimal: Deconstructor[BigDecimal] =
    new Deconstructor[BigDecimal] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: BigDecimal): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val dayOfWeek: Deconstructor[java.time.DayOfWeek] =
    new Deconstructor[java.time.DayOfWeek] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.DayOfWeek): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val duration: Deconstructor[java.time.Duration] =
    new Deconstructor[java.time.Duration] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Duration): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val instant: Deconstructor[java.time.Instant] =
    new Deconstructor[java.time.Instant] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Instant): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val localDate: Deconstructor[java.time.LocalDate] =
    new Deconstructor[java.time.LocalDate] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.LocalDate): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val localDateTime: Deconstructor[java.time.LocalDateTime] =
    new Deconstructor[java.time.LocalDateTime] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.LocalDateTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val localTime: Deconstructor[java.time.LocalTime] =
    new Deconstructor[java.time.LocalTime] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.LocalTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val month: Deconstructor[java.time.Month] =
    new Deconstructor[java.time.Month] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Month): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val monthDay: Deconstructor[java.time.MonthDay] =
    new Deconstructor[java.time.MonthDay] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.MonthDay): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val offsetDateTime: Deconstructor[java.time.OffsetDateTime] =
    new Deconstructor[java.time.OffsetDateTime] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.OffsetDateTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val offsetTime: Deconstructor[java.time.OffsetTime] =
    new Deconstructor[java.time.OffsetTime] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.OffsetTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val period: Deconstructor[java.time.Period] =
    new Deconstructor[java.time.Period] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Period): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val year: Deconstructor[java.time.Year] =
    new Deconstructor[java.time.Year] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Year): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val yearMonth: Deconstructor[java.time.YearMonth] =
    new Deconstructor[java.time.YearMonth] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.YearMonth): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val zoneId: Deconstructor[java.time.ZoneId] =
    new Deconstructor[java.time.ZoneId] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.ZoneId): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val zoneOffset: Deconstructor[java.time.ZoneOffset] =
    new Deconstructor[java.time.ZoneOffset] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.ZoneOffset): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val zonedDateTime: Deconstructor[java.time.ZonedDateTime] =
    new Deconstructor[java.time.ZonedDateTime] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.ZonedDateTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val currency: Deconstructor[java.util.Currency] =
    new Deconstructor[java.util.Currency] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.util.Currency): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val uuid: Deconstructor[java.util.UUID] =
    new Deconstructor[java.util.UUID] {
      def usedRegisters = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.util.UUID): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val none: Deconstructor[None.type] =
    new Deconstructor[None.type] {
      def usedRegisters = RegisterOffset.Zero

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: None.type): Unit = ()
    }

  def some[A](d: Deconstructor[A]): Deconstructor[Some[A]] = d.contramap(_.get)

  def left[A, B](d: Deconstructor[A]): Deconstructor[Left[A, B]] = d.contramap(_.value)

  def right[A, B](d: Deconstructor[B]): Deconstructor[Right[A, B]] = d.contramap(_.value)

  def tuple2[A, B](_1: Deconstructor[A], _2: Deconstructor[B]): Deconstructor[(A, B)] =
    new Deconstructor[(A, B)] {
      def usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B)): Unit = {
        _1.deconstruct(out, baseOffset, in._1)
        _2.deconstruct(out, baseOffset + _1.usedRegisters, in._2)
      }
    }

  def tuple3[A, B, C](_1: Deconstructor[A], _2: Deconstructor[B], _3: Deconstructor[C]): Deconstructor[(A, B, C)] =
    new Deconstructor[(A, B, C)] {
      def usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C)): Unit = {
        _1.deconstruct(out, baseOffset, in._1)
        _2.deconstruct(out, baseOffset + _1.usedRegisters, in._2)
        _3.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters, in._3)
      }
    }

  def tuple4[A, B, C, D](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D]
  ): Deconstructor[(A, B, C, D)] =
    new Deconstructor[(A, B, C, D)] {
      def usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D)): Unit = {
        _1.deconstruct(out, baseOffset, in._1)
        _2.deconstruct(out, baseOffset + _1.usedRegisters, in._2)
        _3.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters, in._3)
        _4.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters + _3.usedRegisters, in._4)
      }
    }

  def tuple5[A, B, C, D, E](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E]
  ): Deconstructor[(A, B, C, D, E)] =
    new Deconstructor[(A, B, C, D, E)] {
      def usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E)): Unit = {
        _1.deconstruct(out, baseOffset, in._1)
        _2.deconstruct(out, baseOffset + _1.usedRegisters, in._2)
        _3.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters, in._3)
        _4.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters + _3.usedRegisters, in._4)
        _5.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters, in._5)
      }
    }

  def tuple6[A, B, C, D, E, F](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F]
  ): Deconstructor[(A, B, C, D, E, F)] =
    new Deconstructor[(A, B, C, D, E, F)] {
      def usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters + _6.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F)): Unit = {
        _1.deconstruct(out, baseOffset, in._1)
        _2.deconstruct(out, baseOffset + _1.usedRegisters, in._2)
        _3.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters, in._3)
        _4.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters + _3.usedRegisters, in._4)
        _5.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters, in._5)
        _6.deconstruct(out, baseOffset + _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters, in._6)
      }
    }
}
