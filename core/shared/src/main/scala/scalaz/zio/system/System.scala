package scalaz.zio.system

import scalaz.zio.ZIO

trait System {
  val system: System.Interface[Any]
}
object System {
  trait Interface[R] {
    def env(variable: String): ZIO[R, Nothing, Option[String]]

    def property(prop: String): ZIO[R, Throwable, Option[String]]

    val lineSeparator: ZIO[R, Nothing, String]
  }
  trait Live extends System {
    object system extends Interface[Any] {
      import java.lang.{ System => JSystem }

      def env(variable: String): ZIO[Any, Nothing, Option[String]] = ZIO.sync(Option(JSystem.getenv(variable)))

      def property(prop: String): ZIO[Any, Throwable, Option[String]] =
        ZIO.syncThrowable(Option(JSystem.getProperty(prop)))

      val lineSeparator: ZIO[Any, Nothing, String] = ZIO.sync(JSystem.lineSeparator)
    }
  }
  object Live extends Live
}
