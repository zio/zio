/*
rule = Zio2Upgrade
*/
package fix

import zio.{ZIO, ZManaged}

import java.io.{FileInputStream, IOException}

object ManagedPieces {
  def make(): ZManaged[Int with String, IOException, String] = ???

  val resource = make()

  val f = (s: String) => ZIO.unit

  resource.use(f)

  ZManaged
    .fromAutoCloseable(zio.blocking.effectBlockingIO(scala.io.Source.fromFile("file.txt")))
    .use(x => ZIO.succeed(x.getLines().length))

  ZManaged.make(ZIO.succeed("hi"))(s => ZIO.succeed("End" + s))

  ZManaged.fromAutoCloseable(
    zio.blocking.effectBlockingIO(new FileInputStream("file.txt"))
  )

}
