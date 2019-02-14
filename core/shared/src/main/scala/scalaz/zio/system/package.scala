package scalaz.zio

package object system extends System.Interface[System] {

  /** Retrieve the value of an environment variable **/
  def env(variable: String): ZIO[System, Nothing, Option[String]] =
    ZIO.accessM(_.system.env(variable))

  /** Retrieve the value of a system property **/
  def property(prop: String): ZIO[System, Throwable, Option[String]] =
    ZIO.accessM(_.system.property(prop))

  /** System-specific line separator **/
  val lineSeparator: ZIO[System, Nothing, String] =
    ZIO.accessM(_.system.lineSeparator)
}
