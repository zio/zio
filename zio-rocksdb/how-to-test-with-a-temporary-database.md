# How to Test with a Temporary Database

> Run tests against a real RocksDB database in a fresh temporary directory that is removed afterwards.

RocksDB is embedded, so tests can use the real thing instead of a mock. In this guide we create a database in a fresh temporary directory per test suite and delete the directory afterwards.

## Create the Directory and the Layer

`ZLayer.scoped` creates a directory when the layer is built, opens the database in it, and on shutdown closes the database and then deletes the directory, because resources are released in reverse order:

```scala
import java.nio.file.{ Files, Path }
import org.rocksdb.Options
import zio._
import zio.rocksdb.RocksDB

def deleteRecursively(path: Path): Unit = {
  val file = path.toFile
  if (file.isDirectory) file.listFiles.foreach(child => deleteRecursively(child.toPath))
  file.delete()
  ()
}

val testDatabase: ZLayer[Any, Throwable, RocksDB] =
  ZLayer.scoped {
    for {
      directory <- ZIO.acquireRelease(ZIO.attempt(Files.createTempDirectory("zio-rocksdb-test")))(path =>
                     ZIO.attempt(deleteRecursively(path)).orDie
                   )
      db <- RocksDB.Live.open(new Options().setCreateIfMissing(true), directory.toString)
    } yield db
  }
```

## Use It in a Spec

To run a spec against the database, add `zio-test` to your test dependencies and provide the layer to the suite with `Spec#provideShared`:

```scala
import zio.test._

object StoreSpec extends ZIOSpecDefault {
  def spec =
    suite("store")(
      test("put then get") {
        for {
          _     <- RocksDB.put("k".getBytes("UTF-8"), "v".getBytes("UTF-8"))
          found <- RocksDB.get("k".getBytes("UTF-8"))
        } yield assertTrue(found.map(new String(_, "UTF-8")) == Some("v"))
      }
    ).provideShared(testDatabase)
}
```

`Spec#provideShared` builds the database once for the whole spec, which is faster; use `Spec#provide` for a fresh database per test.
