package scalaz.zio.stacktracer.impl

import scalaz.zio.stacktracer.SourceLocation

object GlobalConcurrentHashMapCache {
  val globalMutableSharedSourceLocationCache =
    new java.util.concurrent.ConcurrentHashMap[Class[_], SourceLocation](10000)
}
