package zio.examples.test

import zio.test.mock.mockable
import zio.ZIO

object DiffrentScopeExample {

  trait Service {

    def get(key: String): ZIO[Any, Nothing, Int]
    def set(key: String, value: Int): ZIO[Any, Nothing, Unit]
  }
}

@mockable[DiffrentScopeExample.Service]
object MockableMacroExample
