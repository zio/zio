package zio

import java.io.IOException
import java.nio.file.Path

import zio.blocking._
import zio.blocking.Blocking

trait ZManaged {


  final case class FileInputStream private (private val fis: java.io.FileInputStream) {
    def readAll: ZIO[Blocking, IOException, Option[Chunk[Byte]]]       = ???
    def readN(n: Int): ZIO[Blocking, IOException, Option[Chunk[Byte]]] = effectBlocking {
      ???
    }
    def skip(n: Long): ZIO[Blocking, IOException, Long]                = ???
  }

  def readFile(path: Path): ZManaged[Blocking, IOException, java.io.FileInputStream] = ???


}


object ZManaged {

}
