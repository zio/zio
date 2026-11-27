package zio.test.junit

import scala.util.Try

private[zio] object ReflectionUtils {

  /**
   * Returns the Scala object of `klass`: the object itself when `klass` is the
   * class of an object (its name ends with `\$`), or the companion object of
   * `klass` otherwise. Returns `None` when there is no such object. The
   * companion object's class is loaded with the class loader of the ZIO JUnit
   * engine. It uses Java reflection instead of Scala runtime reflection, which
   * Scala 3 doesn't have.
   *
   * @param klass
   *   the class whose object to return
   * @return
   *   the object, or `None`
   */
  def getCompanionObject(klass: Class[_]): Option[Any] =
    Try {
      (if (klass.getName.endsWith("$")) klass else getClass.getClassLoader.loadClass(klass.getName + "$"))
        .getDeclaredField("MODULE$")
        .get(null)
    }.toOption
}
