package zio.config.experimental

import zio._

trait IndexedFlat extends ConfigProvider.Flat {
  import IndexedFlat._

  def enumerateChildrenIndexed(path: ConfigPath)(implicit trace: Trace): IO[Config.Error, Set[ConfigPath]]

  def loadIndexed[A](path: ConfigPath, config: Config.Primitive[A], split: Boolean)(implicit
    trace: Trace
  ): IO[Config.Error, Chunk[A]]

  final def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
    enumerateChildrenIndexed(ConfigPath.fromPath(path)).map(_.map(_.last.value))

  final def load[A](path: Chunk[String], config: Config.Primitive[A])(implicit
    trace: Trace
  ): IO[Config.Error, Chunk[A]] =
    load(path, config, true)

  final override def load[A](path: Chunk[String], config: Config.Primitive[A], split: Boolean)(implicit
    trace: Trace
  ): IO[Config.Error, Chunk[A]] =
    loadIndexed(ConfigPath.fromPath(path), config, split)

  final def loadIndexed[A](path: ConfigPath, config: Config.Primitive[A])(implicit
    trace: Trace
  ): IO[Config.Error, Chunk[A]] =
    loadIndexed(path, config, true)
}

object IndexedFlat {

  type ConfigPath = Chunk[KeyComponent]

  object ConfigPath {

    def fromPath(path: Chunk[String]): ConfigPath =
      path.flatMap(fromString)

    def toPath(configPath: ConfigPath): Chunk[String] = {

      def loop(configPath: List[KeyComponent], path: List[String]): Chunk[String] =
        configPath match {
          case KeyComponent.KeyName(value) :: KeyComponent.Index(index) :: tail =>
            loop(tail, s"$value[$index]" :: path)
          case KeyComponent.KeyName(value) :: tail =>
            loop(tail, value :: path)
          case Nil =>
            Chunk.fromIterable(path.reverse)
        }

      loop(configPath.toList, Nil)
    }

    private lazy val strIndexRegex = """(^.+)(\[(\d+)\])$""".stripMargin.r

    def fromString(string: String): ConfigPath =
      strIndexRegex
        .findPrefixMatchOf(string)
        .filter(_.group(0).nonEmpty)
        .flatMap { regexMatched =>
          val optionalString: Option[String] = Option(regexMatched.group(1))
            .flatMap(s => if (s.isEmpty) None else Some(s))

          val optionalIndex: Option[Int] = Option(regexMatched.group(3))
            .flatMap(s => if (s.isEmpty) None else scala.util.Try(s.toInt).toOption)

          optionalString.flatMap(str => optionalIndex.map(ind => (str, ind)))
        }
        .fold[ConfigPath](Chunk(KeyComponent.KeyName(string))) { case (str, ind) =>
          Chunk(KeyComponent.KeyName(str), KeyComponent.Index(ind))
        }

    def patch(patch: ConfigProvider.Flat.PathPatch)(path: ConfigPath): Either[Config.Error, ConfigPath] =
      patch(toPath(path)).map(fromPath)
  }

  sealed trait KeyComponent { self =>

    def mapName(f: String => String): KeyComponent =
      self match {
        case KeyComponent.KeyName(name) => KeyComponent.KeyName(f(name))
        case KeyComponent.Index(value)  => KeyComponent.Index(value)
      }

    def value: String =
      self match {
        case KeyComponent.KeyName(name) => name
        case KeyComponent.Index(value)  => s"[$value]"
      }
  }

  object KeyComponent {
    final case class Index(index: Int)     extends KeyComponent
    final case class KeyName(name: String) extends KeyComponent
  }
}
