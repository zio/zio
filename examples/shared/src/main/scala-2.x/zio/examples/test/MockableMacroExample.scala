package zio.examples.test

import zio.test.mock.mockable
import zio.{IO, Task, UIO, URIO, ZIO}

object DiffrentScopeExample {

  trait Service {

    def get(key: String): ZIO[Any, Nothing, Int]
    def set(key: String, value: Int): ZIO[Any, Nothing, Unit]
    def io: IO[String, Long]
    def task: Task[Long]
    def uio: UIO[Long]
    def urio: URIO[String, Long]
  }
}

@mockable[DiffrentScopeExample.Service]
object MockableMacroExample
