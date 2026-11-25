# TestFtp

> `TestFtp` is a test double for ZIO FTP that works on a local directory instead of a server. It implements every `FtpAccessors` operation with local file system calls, so code written against the `FtpAccessors` interface can be tested without starting an FTP or SFTP server.

## Definition

`TestFtp.create` builds the service, and the `stub` function in the `zio.ftp` package object wraps it in a layer. Both take the directory that plays the role of the server's root, as a ZIO NIO `Path`:

```scala
object TestFtp {
  def create(root: zio.nio.file.Path): FtpAccessors[Unit]
}

package object ftp {
  type StubFtp = FtpAccessors[Unit]
  def stub(path: zio.nio.file.Path): Layer[Any, StubFtp]
}
```

The `StubFtp` object offers the same accessor functions as `Ftp` and `SFtp`, documented in the [Ftp API](ftp-accessors.md). The underlying client type is `Unit`, so `StubFtp.execute` passes `()` to the function.

:::note
Open pull request [#464](https://github.com/zio/zio-ftp/pull/464) proposes removing the ZIO NIO dependency. If it is merged, `TestFtp.create` and `stub` would take a `java.nio.file.Path` instead of a `zio.nio.file.Path`. Until then, convert a Java path with `zio.nio.file.Path.fromJava`.
:::

## How Paths Map to the Root

Every path you pass is joined to the root directory: `/reports/a.csv` with root `/tmp/ftp-root` becomes `/tmp/ftp-root/reports/a.csv`. The leading `/` is optional. The stub does not normalize `..` segments, so a path containing them can leave the root. Paths in returned `FtpResource` values are relative to the root and start with `/`, so they look like server paths.

## Example

The following setup creates a temporary root directory with one file and a subdirectory, and defines a helper that runs an effect against the stub layer:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.nio.file.Files

val root = Files.createTempDirectory("stub-ftp")
Files.createDirectories(root.resolve("dir1"))
Files.write(root.resolve("notes.txt"), "Hello from the stub".getBytes)
Files.write(root.resolve("dir1/users.csv"), "id,name\n1,Ada\n".getBytes)

def runStub[A](effect: ZIO[StubFtp, Throwable, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(effect.provide(stub(zio.nio.file.Path.fromJava(root))))
      .getOrThrowFiberFailure()
  }
```

Listing the root returns both entries, with paths relative to the root:

```scala
runStub(StubFtp.ls("/").map(_.path).runCollect.map(_.sorted))
// res3: Chunk[String] = IndexedSeq("/dir1", "/notes.txt")
```

`StubFtp.lsDescendant` returns only the files, from every level:

```scala
runStub(StubFtp.lsDescendant("/").map(_.path).runCollect.map(_.sorted))
// res4: Chunk[String] = IndexedSeq("/dir1/users.csv", "/notes.txt")
```

Uploads write real files under the root, so a later read sees them:

```scala
runStub(
  StubFtp.upload("/dir1/new.txt", ZStream.fromIterable("uploaded".getBytes)) *>
    StubFtp.readFile("/dir1/new.txt").via(ZPipeline.utf8Decode).runCollect.map(_.mkString)
)
// res5: String = "uploaded"
```

Reading a missing file fails with the same `InvalidPathError` as on FTP:

```scala
runStub(StubFtp.readFile("/missing.txt").runCollect.either)
// res6: Either[java.io.IOException, Chunk[Byte]] = Left(
//   value = InvalidPathError(message = "File does not exist /missing.txt")
// )
```

## Behavior

The stub follows FTP's behavior where it can, and the local file system's otherwise:

- `FtpAccessors#stat` returns `None` for a missing path and fills `isDirectory`, `size`, `lastModified` and `permissions` from the file system. On Windows, `permissions` is empty.
- `FtpAccessors#ls` and `FtpAccessors#lsDescendant` return an empty stream for a missing directory. `FtpAccessors#lsDescendant` emits regular files only.
- `FtpAccessors#readFile` reads the whole file into memory before streaming it, ignores `chunkSize`, and skips `fileOffset` bytes. A missing file fails with `InvalidPathError`.
- `FtpAccessors#upload` replaces an existing file and fails if the parent directory doesn't exist.
- `FtpAccessors#mkdir` creates missing parent directories, like SFTP and unlike FTP.
- `FtpAccessors#rm` and `FtpAccessors#rmdir` both delete the path, and fail on a non-empty directory.
- `FtpAccessors#rename` moves the file or directory and fails if the target exists.
- Errors other than a missing file for `FtpAccessors#readFile` are plain `IOException` values; see [Errors](errors.md).

## Testing Code Written for Ftp or SFtp

The accessor functions are tied to their environment type: code that calls `Ftp.ls` requires `Ftp`, and a `StubFtp` layer can't satisfy it, because `Ftp` and `StubFtp` are different types. To test such code with the stub, write it against `FtpAccessors[_]` instead of the `Ftp` or `SFtp` objects, and pass in the service. [Test Without a Server](../guides/test-without-a-server.md) shows this pattern with ZIO Test.
