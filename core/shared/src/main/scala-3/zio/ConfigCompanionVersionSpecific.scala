package zio

import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror

private[zio] trait ConfigCompanionVersionSpecific {

  inline def derived[A](using mirror: Mirror.Of[A]): Config[A] =
    inline mirror match {
      case product: Mirror.ProductOf[A] =>
        deriveProduct[A, product.MirroredElemTypes, product.MirroredElemLabels](product)
      case _ =>
        compiletime.error("Config can only be derived for product types")
    }

  private inline def deriveProduct[A, Elems <: Tuple, Labels <: Tuple](
    mirror: Mirror.ProductOf[A]
  ): Config[A] =
    combineFields(fieldConfigs[Elems, Labels]).map { values =>
      mirror.fromProduct(Tuple.fromArray(values.toArray))
    }

  private inline def fieldConfigs[Elems <: Tuple, Labels <: Tuple]: List[Config[Any]] =
    inline erasedValue[(Elems, Labels)] match {
      case _: (EmptyTuple, EmptyTuple) =>
        Nil
      case _: ((elem *: elems), (label *: labels)) =>
        configFor[elem]
          .nested(constValue[label].asInstanceOf[String])
          .asInstanceOf[Config[Any]] :: fieldConfigs[elems, labels]
    }

  private def combineFields(configs: List[Config[Any]]): Config[List[Any]] =
    configs match {
      case Nil =>
        Config.succeed(Nil)
      case head :: tail =>
        tail.foldLeft(head.map(List(_))) { (acc, config) =>
          acc.zipWith(config)((values, value) => values :+ value)
        }
    }

  private inline def configFor[A]: Config[A] =
    inline erasedValue[A] match {
      case _: String                  => Config.string.asInstanceOf[Config[A]]
      case _: Boolean                 => Config.boolean.asInstanceOf[Config[A]]
      case _: Byte                    => Config.bigInt.map(_.toByte).asInstanceOf[Config[A]]
      case _: Short                   => Config.bigInt.map(_.toShort).asInstanceOf[Config[A]]
      case _: Int                     => Config.int.asInstanceOf[Config[A]]
      case _: Long                    => Config.long.asInstanceOf[Config[A]]
      case _: Float                   => Config.float.asInstanceOf[Config[A]]
      case _: Double                  => Config.double.asInstanceOf[Config[A]]
      case _: BigInt                  => Config.bigInt.asInstanceOf[Config[A]]
      case _: BigDecimal              => Config.bigDecimal.asInstanceOf[Config[A]]
      case _: zio.Duration            => Config.duration.asInstanceOf[Config[A]]
      case _: java.time.LocalDate     => Config.localDate.asInstanceOf[Config[A]]
      case _: java.time.LocalTime     => Config.localTime.asInstanceOf[Config[A]]
      case _: java.time.LocalDateTime => Config.localDateTime.asInstanceOf[Config[A]]
      case _: java.time.OffsetDateTime =>
        Config.offsetDateTime.asInstanceOf[Config[A]]
      case _: java.net.URI       => Config.uri.asInstanceOf[Config[A]]
      case _: Config.Secret      => Config.secret.asInstanceOf[Config[A]]
      case _: Option[t]          => configFor[t].optional.asInstanceOf[Config[A]]
      case _: List[t]            => Config.listOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Seq[t]             => Config.listOf(configFor[t]).map(_.toSeq).asInstanceOf[Config[A]]
      case _: Set[t]             => Config.setOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Vector[t]          => Config.vectorOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Chunk[t]           => Config.chunkOf(configFor[t]).asInstanceOf[Config[A]]
      case _: NonEmptyChunk[t]   => Config.nonEmptyChunkOf(configFor[t]).asInstanceOf[Config[A]]
      case _: Map[String, value] => Config.table(configFor[value]).asInstanceOf[Config[A]]
      case _                     => summonInline[Config[A]]
    }
}
