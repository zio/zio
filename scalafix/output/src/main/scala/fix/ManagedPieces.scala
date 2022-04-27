package fix

import zio.{Scope, ZIO}

import java.io.{FileInputStream, IOException}

object ManagedPieces {
  def make(): ZIO[Int with String with Scope, IOException, String] = ???

  val resource = make()
  ZIO.scoped {
    resource.flatMap(f)
  }

  val f = (s: String) => ZIO.unit

  ZIO.scoped {
    resource.flatMap(f)
  }

  ZIO.scoped {
    ZIO
    .fromAutoCloseable(ZIO.attemptBlockingIO(scala.io.Source.fromFile("file.txt")))
    .flatMap(x => ZIO.succeed(x.getLines().length))
  }

  ZIO.acquireRelease(ZIO.succeed("hi"))(s => ZIO.succeed("End" + s))

  ZIO.fromAutoCloseable(
    ZIO.attemptBlockingIO(new FileInputStream("file.txt"))
  )
}
