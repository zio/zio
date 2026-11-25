# List a Directory Recursively

> Walk a remote directory tree with lsDescendant, filter and summarize the files, and write a custom walk that includes directories or stops at a depth.

In this guide, we list every file under a remote directory, filter the listing by name and size, summarize it, and then write our own walk for the two things `FtpAccessors#lsDescendant` doesn't do: including the directories themselves and stopping at a given depth.

## The Problem

FTP and SFTP servers only list one directory at a time. Walking a tree means listing a directory, telling files from subdirectories, and listing each subdirectory in turn, which with the Java client looks like this:

```scala
import org.apache.commons.net.ftp.FTPClient

def allFiles(client: FTPClient, dir: String): List[String] =
  client.listFiles(dir).toList.flatMap { f =>
    val path = s"$dir/${f.getName}"
    if (f.isDirectory) allFiles(client, path) else List(path)
  }
```

This version builds the whole list in memory, blocks the calling thread, and throws on the first error. ZIO FTP's `FtpAccessors#lsDescendant` does the same walk as a lazy `ZStream`, so you can filter, count or stop early while the walk is in progress.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md). The functions below take the connection as an `FtpAccessors[Any]` parameter, so they work with `Ftp`, `SFtp` and the `StubFtp` test double alike; with the accessor objects, `Ftp.lsDescendant(...)` works the same way. To show real output on this page, we run them against a stub server holding a small photo archive:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.io.IOException
import java.nio.file.Files

val disk = Files.createTempDirectory("photo-server")
Files.createDirectories(disk.resolve("photos/2025/summer"))
Files.createDirectories(disk.resolve("photos/2026"))
Files.write(disk.resolve("photos/2025/summer/beach.jpg"), Array.fill[Byte](3000)(0))
Files.write(disk.resolve("photos/2026/snow.jpg"), Array.fill[Byte](2000)(0))
Files.write(disk.resolve("photos/2026/notes.txt"), Array.fill[Byte](100)(0))

def onServer[A](f: FtpAccessors[Any] => ZIO[Any, IOException, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(ZIO.serviceWithZIO[StubFtp](f).provide(stub(zio.nio.file.Path.fromJava(disk))))
      .getOrThrowFiberFailure()
  }
```

## Listing Every File

`FtpAccessors#lsDescendant` streams every file below a directory, from every level. Directories themselves are not emitted:

```scala
onServer(ftp => ftp.lsDescendant("/photos").map(_.path).runCollect.map(_.sorted))
// res6: Chunk[String] = IndexedSeq(
//   "/photos/2025/summer/beach.jpg",
//   "/photos/2026/notes.txt",
//   "/photos/2026/snow.jpg"
// )
```

The walk is lazy: each subdirectory is listed only when the stream reaches it. On SFTP and `StubFtp`, a directory that doesn't exist produces an empty stream rather than a failure; on FTP the result depends on the server. Check with `FtpAccessors#stat` first if you need to tell "empty" from "missing".

## Filtering and Summarizing

Because the result is a `ZStream`, the usual stream operators apply. To keep only the JPEG files and add up their sizes:

```scala
onServer { ftp =>
  ftp
    .lsDescendant("/photos")
    .filter(_.path.toLowerCase.endsWith(".jpg"))
    .runFold((0, 0L)) { case ((count, bytes), file) => (count + 1, bytes + file.size) }
}
// res7: (Int, Long) = (2, 5000L)
```

To stop as soon as a match is found, use `ZStream#runHead` or `ZStream#take`; the walk stops listing directories once the stream has what it needs:

```scala
onServer(ftp => ftp.lsDescendant("/photos").filter(_.size > 2500).map(_.path).runHead)
// res8: Option[String] = Some(value = "/photos/2025/summer/beach.jpg")
```

## Including Directories

Sometimes you need the directories too, for example to recreate the tree elsewhere. `FtpAccessors#ls` lists one level with both files and directories, so a walk that emits every entry and descends into directories is a few lines:

```scala
def walk(ftp: FtpAccessors[Any], dir: String): ZStream[Any, IOException, FtpResource] =
  ftp.ls(dir).flatMap { entry =>
    if (entry.isDirectory.contains(true)) ZStream.succeed(entry) ++ walk(ftp, entry.path)
    else ZStream.succeed(entry)
  }
```

Each directory appears before its contents:

```scala
onServer(ftp => walk(ftp, "/photos").map(e => if (e.isDirectory.contains(true)) s"${e.path}/" else e.path).runCollect.map(_.sorted))
// res9: Chunk[String] = IndexedSeq(
//   "/photos/2025/",
//   "/photos/2025/summer/",
//   "/photos/2025/summer/beach.jpg",
//   "/photos/2026/",
//   "/photos/2026/notes.txt",
//   "/photos/2026/snow.jpg"
// )
```

`isDirectory` is an `Option[Boolean]`, so we test it with `Option#contains`; `FtpAccessors#ls` always fills it, but `FtpAccessors#stat` on SFTP leaves it as `None`.

## Limiting the Depth

A deep tree can take a long time to walk. To stop at a given depth, pass the remaining depth down and only descend while it is above one:

```scala
def walkTo(ftp: FtpAccessors[Any], dir: String, depth: Int): ZStream[Any, IOException, FtpResource] =
  ftp.ls(dir).flatMap { entry =>
    if (entry.isDirectory.contains(true) && depth > 1) ZStream.succeed(entry) ++ walkTo(ftp, entry.path, depth - 1)
    else ZStream.succeed(entry)
  }
```

With a depth of 2, the walk lists the two levels below `/photos`, so `/photos/2025/summer` appears but the files inside it do not:

```scala
onServer(ftp => walkTo(ftp, "/photos", 2).map(_.path).runCollect.map(_.sorted))
// res10: Chunk[String] = IndexedSeq(
//   "/photos/2025",
//   "/photos/2025/summer",
//   "/photos/2026",
//   "/photos/2026/notes.txt",
//   "/photos/2026/snow.jpg"
// )
```

## Using It with a Real Server

The functions above take any connection. With a real server, take the service from the environment and provide its layer:

```scala
val settings = UnsecureFtpSettings("127.0.0.1", 2121, PasswordCredentials("username", "userpass"))

val tree: ZIO[Any, IOException, Chunk[FtpResource]] =
  ZIO.serviceWithZIO[Ftp](ftp => walk(ftp, "/").runCollect).provide(unsecure(settings))
```

## Going Further

For the fields of each listed entry, see [FtpResource](../reference/ftp-resource.md); on FTP, `FtpAccessors#ls` timestamps often have only minute precision. For `FtpAccessors#ls`, `FtpAccessors#lsDescendant` and `FtpAccessors#stat` in detail, see the [Ftp API](../reference/ftp-accessors.md#inspecting-files) reference.
