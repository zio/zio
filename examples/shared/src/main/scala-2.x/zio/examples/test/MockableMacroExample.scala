package zio.examples.test

import zio.test.mock.Mockable
import zio.ZIO

object DiffrentScopeExample {

  trait Service {

    def get(key: String): ZIO[Any, Nothing, Int]
    def set(key: String, value: Int): ZIO[Any, Nothing, Unit]
  }
}

@Mockable[DiffrentScopeExample.Service]
object MockableMacroExample
