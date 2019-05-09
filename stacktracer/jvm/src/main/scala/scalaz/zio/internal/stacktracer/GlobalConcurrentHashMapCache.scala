package scalaz.zio.internal.stacktracer

object GlobalConcurrentHashMapCache {
  val globalMutableSharedSourceLocationCache =
    new java.util.concurrent.ConcurrentHashMap[Class[_], ZTraceElement](10000)
}
