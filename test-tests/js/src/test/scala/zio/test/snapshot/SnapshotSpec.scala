package zio.test.snapshot

import zio.UIO
import zio.test.Assertion.equalTo
import zio.test.{ZIOBaseSpec, _}

import java.nio.file.Path
import scala.io.Source
import scalajs.js.special.fileLevelThis

// Shared
trait FileReader {
  def readFile(path: Path): UIO[Option[String]] = ???
}

object SnapshotSpec extends DefaultRunnableSpec {

  def getFile(implicit sourceLocation: SourceLocation) = {
    println(sourceLocation)
//    scalajs.js.special
//    println(Source.fromFile(sourceLocation.path).getLines().mkString)
    Fs.readFile(
      sourceLocation.path,
      "UTF-8",
      { (err, result) =>
        println("ERR", err)
        println("RESULT", result)
      }
    )
  }

  override def spec: ZSpec[Any, Any] = suite("HUH")(
    test("OKAY") {
      getFile
//      val right: Option[Int] = Right(10)
//      assertTrue(right.get == 10)
      println(s"HELLO ${fileLevelThis}")
      val okay = "cool"
      assertTrue(okay == "no")
//      assert(okay)(equalTo("hello"))
    }
  )
}
