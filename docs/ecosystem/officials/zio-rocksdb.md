---
id: zio-rocksdb
title: "ZIO RocksDB"
---

[ZIO RocksDB](https://github.com/zio/zio-rocksdb) is a ZIO-based interface to RocksDB.

Rocksdb is an embeddable persistent key-value store that is optimized for fast storage. ZIO RocksDB provides us a functional ZIO wrapper around its Java API.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-rocksdb" % "0.3.0" 
```

## Example

An example of writing and reading key/value pairs and also using transactional operations when using RocksDB:

```scala
import zio.console._
import zio.rocksdb.{RocksDB, Transaction, TransactionDB}
import zio.{URIO, ZIO}

import java.nio.charset.StandardCharsets._

object ZIORocksDBExample extends zio.App {

  private def bytesToString(bytes: Array[Byte]): String = new String(bytes, UTF_8)
  private def bytesToInt(bytes: Array[Byte]): Int = bytesToString(bytes).toInt

  val job1: ZIO[Console with RocksDB, Throwable, Unit] =
    for {
      _ <- RocksDB.put(
        "Key".getBytes(UTF_8),
        "Value".getBytes(UTF_8)
      )
      result <- RocksDB.get("Key".getBytes(UTF_8))
      stringResult = result.map(bytesToString)
      _ <- putStrLn(s"value: $stringResult")
    } yield ()


  val job2: ZIO[Console with TransactionDB, Throwable, Unit] =
    for {
      key <- ZIO.succeed("COUNT".getBytes(UTF_8))
      _ <- TransactionDB.put(key, 0.toString.getBytes(UTF_8))
      _ <- ZIO.foreachPar(0 until 10) { _ =>
        TransactionDB.atomically {
          Transaction.getForUpdate(key, exclusive = true) >>= { iCount =>
            Transaction.put(key, iCount.map(bytesToInt).map(_ + 1).getOrElse(-1).toString.getBytes(UTF_8))
          }
        }
      }
      value <- TransactionDB.get(key)
      counterValue = value.map(bytesToInt)
      _ <- putStrLn(s"The value of counter: $counterValue") // Must be 10
    } yield ()

  private val transactional_db =
    TransactionDB.live(new org.rocksdb.Options().setCreateIfMissing(true), "tr_db")

  private val rocks_db =
    RocksDB.live(new org.rocksdb.Options().setCreateIfMissing(true), "rocks_db")

  override def run(args: List[String]): URIO[zio.ZEnv, Int] =
    (job1 <*> job2)
      .provideCustom(transactional_db ++ rocks_db)
      .foldCauseZIO(cause => putStrLn(cause.prettyPrint) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
```
