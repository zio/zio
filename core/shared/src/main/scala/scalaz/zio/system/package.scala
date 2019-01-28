package scalaz.zio

package object system extends System.Interface[System] {

  /** Retrieve the value of an environment variable **/
  def env(variable: String): ZIO[System, Nothing, Option[String]] =
    ZIO.readM(_.system.env(variable))

  /** Retrieve the value of a system property **/
  def property(prop: String): ZIO[System, Throwable, Option[String]] =
    ZIO.readM(_.system.property(prop))

  /** System-specific line separator **/
  val lineSeparator: ZIO[System, Nothing, String] =
    ZIO.readM(_.system.lineSeparator)
}
