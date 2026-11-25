# Tutorial: Back Up a Folder to a File Server

> Build a small backup tool with ZIO FTP: upload a folder tree, skip unchanged files, verify the copy, then point it at a real server.

Welcome! In this tutorial we build a small backup tool: it copies a local folder, with its subfolders, to a file server, skips files that are already there, and checks that the copy matches. This tutorial is for Scala developers who know the basics of `ZIO` effects. You don't need any prior experience with ZIO FTP or with FTP servers.

## Learning Objectives

By the end of this tutorial, you will understand:

- How a ZIO FTP connection is provided as a layer and used as a service.
- How uploads, listings and downloads work as ZIO effects and streams.
- How to create remote directories and check whether a file exists.
- How to write file-transfer code once and run it against a test double or a real server.

We'll learn these concepts through:

1. Preparing a folder to back up
2. Connecting to a server
3. Uploading one file
4. Uploading a whole folder
5. Skipping unchanged files
6. Verifying the backup
7. Backing up to a real server

We recommend reading from top to bottom: each section builds on the previous one.

## Background

A file server stores files under paths like `/backup/photos/cat.jpg`, and a client sends it commands: list this directory, store these bytes, send me that file. ZIO FTP gives every command the shape of a ZIO value. Commands that return one thing, like "create a directory", are `ZIO` effects; commands that return many things or many bytes, like "list a directory" or "read a file", are `ZStream`s. Nothing happens until the effect runs, and the connection itself is a resource that opens before the first command and closes after the last.

So that every step runs right here on the page, we use ZIO FTP's test double, `StubFtp`, which behaves like a server but stores its files in a local directory. In the last section, we point the same code at a real FTP server.

## 1. Prepare a Folder to Back Up

First, we need something to back up. Let's create a temporary folder with a text file and two "photos", and a second, empty temporary folder that plays the role of the server's disk:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.io.IOException
import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters._

val source = Files.createTempDirectory("my-documents")
Files.createDirectories(source.resolve("photos"))
Files.write(source.resolve("notes.txt"), "Buy milk".getBytes)
Files.write(source.resolve("photos/cat.jpg"), Array.fill[Byte](1200)(1))
Files.write(source.resolve("photos/dog.jpg"), Array.fill[Byte](800)(2))

val serverDisk = Files.createTempDirectory("server-disk")
```

The code above does the following:

- The imports bring in ZIO, ZIO FTP, ZIO Streams and the Java file API.
- `source` is the folder to back up. It holds `notes.txt` and a `photos` subfolder with two files of 1200 and 800 bytes.
- `serverDisk` is where the stub server keeps the files we upload. It starts empty.

## 2. Connect to a Server

A ZIO FTP connection is a service in the ZIO environment, and a layer creates it. For the stub, the layer is `stub(root)`; for a real FTP server, it is `unsecure(settings)`. Our backup code receives the service as a plain parameter of type `FtpAccessors[Any]`, which every connection type satisfies, so the same code works with any of them. Let's write a helper that builds the stub layer, hands its service to a function, and runs the result:

```scala
def runOnStub[A](f: FtpAccessors[Any] => ZIO[Any, IOException, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(ZIO.serviceWithZIO[StubFtp](f).provide(stub(zio.nio.file.Path.fromJava(serverDisk))))
      .getOrThrowFiberFailure()
  }
```

The helper has three parts:

- `ZIO.serviceWithZIO[StubFtp](f)` takes the connection out of the environment and passes it to `f`.
- `ZIO#provide` with `stub(...)` builds the stub layer around the `serverDisk` folder. A real program provides `unsecure` or `secure` here instead.
- `Runtime.default.unsafe.run` runs the effect so the page can show the result. A real program extends `ZIOAppDefault` instead, as we do in section 7.

Now let's ask the server what is in its root directory. `FtpAccessors#ls` returns a stream of entries, and `ZStream#runCollect` gathers them:

```scala
runOnStub(ftp => ftp.ls("/").map(_.path).runCollect)
// res4: Chunk[String] = IndexedSeq()
```

The listing is empty: the server has no files yet.

## 3. Upload One File

To upload a file, we need a directory to put it in and a stream of its bytes. `FtpAccessors#mkdir` creates the directory, `ZStream.fromPath` reads the local file as a stream, and `FtpAccessors#upload` sends that stream to the server:

```scala
def uploadFile(ftp: FtpAccessors[Any], local: Path, remote: String): ZIO[Any, IOException, Unit] =
  ftp.upload(remote, ZStream.fromPath(local))
```

Let's create `/backup` and upload `notes.txt` into it, then list the directory to check:

```scala
runOnStub { ftp =>
  ftp.mkdir("/backup") *>
    uploadFile(ftp, source.resolve("notes.txt"), "/backup/notes.txt") *>
    ftp.ls("/backup").map(f => s"${f.path} (${f.size} bytes)").runCollect
}
// res5: Chunk[String] = IndexedSeq("/backup/notes.txt (8 bytes)")
```

The file is on the server with its 8 bytes. Notice that the upload never loads the file into memory: `ZStream.fromPath` reads it in chunks while `FtpAccessors#upload` writes them, so the same code works for a file of any size.

## 4. Upload a Whole Folder

A folder backup repeats the single upload for every file, but subfolders add a twist: the remote directory must exist before we upload into it. On FTP servers, `FtpAccessors#mkdir` creates only one level at a time, so we create each level that is missing. `FtpAccessors#stat` tells us whether a path exists by returning `Some(resource)` or `None`:

```scala
def ensureDirectory(ftp: FtpAccessors[Any], dir: String): ZIO[Any, IOException, Unit] = {
  val levels = dir.split('/').filter(_.nonEmpty).scanLeft("")(_ + "/" + _).drop(1)
  ZIO.foreachDiscard(levels.toList) { level =>
    ftp.stat(level).flatMap(existing => ftp.mkdir(level).when(existing.isEmpty))
  }
}
```

The function first turns `/backup/photos` into `/backup` and `/backup/photos`, one entry per level. For each level, `FtpAccessors#stat` checks whether it exists, and `FtpAccessors#mkdir` runs only when it doesn't.

Next, we need the list of local files and a rule that maps each local path to a remote one:

```scala
def localFiles(dir: Path): ZIO[Any, IOException, List[Path]] =
  ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(Files.walk(dir)))(s => ZIO.succeed(s.close())) { s =>
    ZIO.attemptBlockingIO(s.iterator().asScala.filter(Files.isRegularFile(_)).toList.sorted)
  }

def remotePath(local: Path): String =
  "/backup/" + source.relativize(local).asScala.mkString("/")
```

These two helpers do the local side of the work. `localFiles` walks the folder tree on the blocking thread pool and keeps only regular files, sorted so the output is stable. `remotePath` turns `<source>/photos/cat.jpg` into `/backup/photos/cat.jpg`.

Now the backup itself: for each local file, make sure its remote directory exists, then upload it:

```scala
def backupAll(ftp: FtpAccessors[Any]): ZIO[Any, IOException, Int] =
  localFiles(source).flatMap { files =>
    ZIO.foreach(files) { local =>
      val remote = remotePath(local)
      ensureDirectory(ftp, remote.substring(0, remote.lastIndexOf('/'))) *> uploadFile(ftp, local, remote)
    }.map(_.size)
  }
```

Let's run it and list everything under `/backup`. `FtpAccessors#lsDescendant` walks the remote tree and returns every file in it:

```scala
runOnStub { ftp =>
  backupAll(ftp) *> ftp.lsDescendant("/backup").map(f => s"${f.path} (${f.size} bytes)").runCollect.map(_.sorted)
}
// res6: Chunk[String] = IndexedSeq(
//   "/backup/notes.txt (8 bytes)",
//   "/backup/photos/cat.jpg (1200 bytes)",
//   "/backup/photos/dog.jpg (800 bytes)"
// )
```

All three files are on the server, in the same folder structure as on the local disk.

## 5. Skip Unchanged Files

Running the backup again uploads every file again, which is wasteful when most of them haven't changed. Let's upload a file only when the server doesn't have it, or has a copy with a different size. `FtpAccessors#stat` gives us the remote size:

```scala
def needsUpload(ftp: FtpAccessors[Any], local: Path, remote: String): ZIO[Any, IOException, Boolean] =
  for {
    localSize <- ZIO.attemptBlockingIO(Files.size(local))
    existing  <- ftp.stat(remote)
  } yield existing.forall(_.size != localSize)

def backupChanged(ftp: FtpAccessors[Any]): ZIO[Any, IOException, Int] =
  localFiles(source).flatMap { files =>
    ZIO.filter(files)(local => needsUpload(ftp, local, remotePath(local))).flatMap { changed =>
      ZIO.foreach(changed) { local =>
        val remote = remotePath(local)
        ensureDirectory(ftp, remote.substring(0, remote.lastIndexOf('/'))) *> uploadFile(ftp, local, remote)
      }.as(changed.size)
    }
  }
```

The new code changes the backup in two places. `needsUpload` returns true when `FtpAccessors#stat` finds nothing (`Option#forall` on `None` is true) or finds a file whose size differs. `backupChanged` keeps only the files that need uploading, uploads them like `backupAll`, and returns how many it uploaded.

Everything is already on the server, so a run now uploads nothing:

```scala
runOnStub(backupChanged)
// res7: Int = 0
```

Let's change one local file, so its size no longer matches the copy on the server:

```scala
Files.write(source.resolve("notes.txt"), "Buy milk and bread".getBytes)
```

Now we run the backup again. Only the changed file is uploaded:

```scala
runOnStub(backupChanged)
// res9: Int = 1
```

Comparing sizes is a simple rule that misses edits that keep the size the same. A real tool would also compare `lastModified` or a checksum; we keep the size rule so the tutorial stays focused.

## 6. Verify the Backup

A backup is only useful if it can be read back. `FtpAccessors#readFile` streams a remote file's bytes, so we can compare them with the local file:

```scala
def verify(ftp: FtpAccessors[Any]): ZIO[Any, IOException, List[String]] =
  localFiles(source).flatMap { files =>
    ZIO.foreach(files) { local =>
      for {
        remoteBytes <- ftp.readFile(remotePath(local)).runCollect
        localBytes  <- ZIO.attemptBlockingIO(Files.readAllBytes(local))
      } yield s"${remotePath(local)}: ${if (remoteBytes.toArray.sameElements(localBytes)) "OK" else "DIFFERENT"}"
    }
  }
```

The verification reads each remote file in full with `ZStream#runCollect` and compares it byte for byte with the local file. Let's run it:

```scala
runOnStub(verify)
// res10: List[String] = List(
//   "/backup/notes.txt: OK",
//   "/backup/photos/cat.jpg: OK",
//   "/backup/photos/dog.jpg: OK"
// )
```

Every file matches, including the `notes.txt` we changed in the previous section.

## 7. Back Up to a Real Server

Our functions only use `FtpAccessors[Any]`, so they run unchanged against a real server. The only new piece is the layer: instead of `stub`, we provide `unsecure` with the server's settings, and instead of `runOnStub`, we run the program as a `ZIOAppDefault`:

```scala
object BackupApp extends ZIOAppDefault {

  private val settings =
    UnsecureFtpSettings("127.0.0.1", 2121, PasswordCredentials("username", "userpass"))

  private val program: ZIO[Ftp, IOException, Unit] =
    ZIO.serviceWithZIO[Ftp] { ftp =>
      for {
        uploaded <- backupChanged(ftp)
        _        <- Console.printLine(s"Uploaded $uploaded file(s)")
        report   <- verify(ftp)
        _        <- ZIO.foreachDiscard(report)(Console.printLine(_))
      } yield ()
    }

  override def run = program.provide(unsecure(settings))
}
```

The program differs from the stub runs in three places:

- `settings` describes the server; the values match the local server from [Getting Started](../getting-started.md).
- `ZIO.serviceWithZIO[Ftp]` takes the FTP connection from the environment, just as `runOnStub` did with `StubFtp`.
- `ZIO#provide` with `unsecure(settings)` connects before the program starts and disconnects when it ends.

Try it: start the Pure-FTPd container from Getting Started, run `BackupApp`, then run it a second time and notice that it uploads nothing.

## What You've Learned

In this tutorial, you learned:

- How a connection is provided as a layer (`stub`, `unsecure`) and used as an `FtpAccessors` service.
- How `FtpAccessors#upload`, `FtpAccessors#ls`, `FtpAccessors#lsDescendant` and `FtpAccessors#readFile` turn file transfers into effects and streams that can move a file in chunks.
- How `FtpAccessors#stat` and `FtpAccessors#mkdir` let you check for paths and create directory trees one level at a time.
- How code written against `FtpAccessors[Any]` runs on a test double and on a real server without changes.

You now have a working mental model of ZIO FTP. The next step is to apply it to your own servers and protocols.

## Where to Go Next

To keep going:

- **Connecting to SFTP?** Follow [Connect over SFTP with a Key](../guides/connect-over-sftp-with-a-key.md), then provide `secure` instead of `unsecure` and take `SFtp` from the environment instead of `Ftp`.
- **Testing your own code?** [Test Without a Server](../guides/test-without-a-server.md) uses the same stub with ZIO Test.
- **Want every operation in detail?** Read the [Ftp API](../reference/ftp-accessors.md) reference.
- **Wondering how FTP, FTPS and SFTP differ?** Read [FTP, FTPS and SFTP](../protocols.md).
