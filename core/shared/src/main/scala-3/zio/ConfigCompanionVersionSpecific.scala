package zio

import scala.compiletime.{constValue, erasedValue, error, summonInline}
import scala.deriving.Mirror

private[zio] trait ConfigCompanionVersionSpecific {

  inline def derived[A](using mirror: Mirror.Of[A]): Config[A] =
    inline mirror match {
      case product: Mirror.ProductOf[A] => deriveProduct(product)
      case _: Mirror.SumOf[A]           => error("Config can only be derived for product types")
    }

  private inline def deriveProduct[A](mirror: Mirror.ProductOf[A]): Config[A] =
    deriveFields[mirror.MirroredElemLabels, mirror.MirroredElemTypes].map(fields => mirror.fromProduct(fields))

  private inline def deriveFields[Labels <: Tuple, Types <: Tuple]: Config[Types] =
    inline (erasedValue[Labels], erasedValue[Types]) match {
      case (_: EmptyTuple, _: EmptyTuple) =>
        Config.succeed(EmptyTuple).asInstanceOf[Config[Types]]
      case (_: (label *: labels), _: (field *: fields)) =>
        val name = constValue[label].asInstanceOf[String]
        configFor[field].nested(name).zipWith(deriveFields[labels, fields])(_ *: _).asInstanceOf[Config[Types]]
    }

  private inline def configFor[A]: Config[A] =
    inline erasedValue[A] match {
      case _: BigDecimal               => Config.bigDecimal.asInstanceOf[Config[A]]
      case _: BigInt                   => Config.bigInt.asInstanceOf[Config[A]]
      case _: Boolean                  => Config.boolean.asInstanceOf[Config[A]]
      case _: Chunk[t]                 => Config.chunkOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Double                   => Config.double.asInstanceOf[Config[A]]
      case _: Float                    => Config.float.asInstanceOf[Config[A]]
      case _: Int                      => Config.int.asInstanceOf[Config[A]]
      case _: List[t]                  => Config.listOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Long                     => Config.long.asInstanceOf[Config[A]]
      case _: Map[String, value]       => Config.table(configFor[value]).asInstanceOf[Config[A]]
      case _: NonEmptyChunk[t]         => Config.nonEmptyChunkOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Option[t]                => configFor[t].optional.asInstanceOf[Config[A]]
      case _: Config.Secret            => Config.secret.asInstanceOf[Config[A]]
      case _: Set[t]                   => Config.setOf(configFor[t]).asInstanceOf[Config[A]]
      case _: String                   => Config.string.asInstanceOf[Config[A]]
      case _: Vector[t]                => Config.vectorOf(configFor[t]).asInstanceOf[Config[A]]
      case _: java.net.URI             => Config.uri.asInstanceOf[Config[A]]
      case _: java.time.LocalDate      => Config.localDate.asInstanceOf[Config[A]]
      case _: java.time.LocalDateTime  => Config.localDateTime.asInstanceOf[Config[A]]
      case _: java.time.LocalTime      => Config.localTime.asInstanceOf[Config[A]]
      case _: java.time.OffsetDateTime => Config.offsetDateTime.asInstanceOf[Config[A]]
      case _: zio.Duration             => Config.duration.asInstanceOf[Config[A]]
      case _                           => summonInline[Config[A]]
    }
}
