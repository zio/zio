package scalaz.zio.stacktracer.impl

import java.util.concurrent.ConcurrentHashMap

import scalaz.zio.stacktracer.{ SourceLocationCache, SourceLocation }

final class GlobalConcurrentHashMapCache extends SourceLocationCache {
  val cache = new ConcurrentHashMap[AnyRef, SourceLocation]

  override def getOrElseUpdate(lambda: AnyRef, f: AnyRef => SourceLocation): SourceLocation = {
    // not using atomic ops to avoid needless spin-lock
    // because `f` is repeatable – if the value is there it must be the same
    val clazz = lambda.getClass

    val res = cache.get(clazz)
//    val res = cache.get(lambda)
    if (res == null) {
      val v = f(lambda)
//      cache.putIfAbsent(lambda, v)
      cache.putIfAbsent(clazz, v)
      v
    } else {
      System.err.println(s"cache hit $clazz")
      res
    }
  }
}
