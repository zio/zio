package zio.examples.test

import zio.test.mock.MethodTags
import zio.ZIO

object DiffrentScopeExample {

  trait Service {

    def get(key: String): ZIO[Any, Nothing, Int]
    def set(key: String, value: Int): ZIO[Any, Nothing, Unit]
  }

}

@MethodTags[DiffrentScopeExample.Service]
object MethodTagsMacrosExample {

}
