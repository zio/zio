# Zip4J

> The `zip4j` module creates and reads ZIP archives with the [Zip4J](https://github.com/srikanth-lingala/zip4j) library. Use it when you need to read password-protected archives. It requires every entry to declare its uncompressed size when you write one. Use the `Zip` module when you cannot provide the uncompressed size up front.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "3.0.1"
```

## Reading Archives

`Zip4JUnarchiver` is an `Unarchiver` that reads ZIP archives. Each entry comes with its content as a stream. Use `Zip4JUnarchiver.apply` with a password for a password-protected archive:

```scala
import zio.compress._
import zio.stream._

val contents: ZStream[Any, Throwable, (String, Long)] =
  ZStream
    .fromFileName("secret.zip")
    .via(Zip4JUnarchiver(password = Some("it is a secret")).list)
    .map(entry => entry.name -> entry.uncompressedSize.getOrElse(-1L))
```

For an archive without a password, omit the `password` argument, or pass `None`:

```scala
import zio.compress._
import zio.stream._

val unprotected = ZStream.fromFileName("archive.zip").via(Zip4JUnarchiver().unarchive)
```

`Zip4JUnarchiver#list` reads each entry's content to fill in the uncompressed size. `Zip4JUnarchiver#unarchive` gives the content as a stream, and it must be fully read before the next entry is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md).

## Writing Archives

`Zip4JArchiver` is an `Archiver` that writes ZIP archives. Every entry must have a known uncompressed size, so the entry type is `ArchiveEntry[Some, _]`:

```scala
import zio.compress._
import zio.stream._

val content = "Hello, ZIO!".getBytes("UTF-8")

val entry = ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(content.length.toLong)

val archive = ZStream((entry, ZStream.fromIterable(content))).via(Zip4JArchiver().archive)
```

If the number of bytes written does not match the declared size, the stream dies with an `IllegalStateException`.

:::warning[Standard encryption is very weak]
`Zip4JArchiver(password = Some(...))` uses the standard Zip encryption which is very weak. Use `Zip4JArchiver.encrypted(...)` to set a better encryption method (see below).
:::

:::warning[Encrypting with 1.x or 2.x]
Prior to version 3.0.0, `Zip4JArchiver(password = Some(...))` does not encrypt the entries. The archive it writes is identical to the archive written without a password. Do not rely on this option to protect data. Reading password-protected archives works.
:::

## Options

| Option      | Type                   | Default              |
|-------------|------------------------|----------------------|
| `password`  | `Option[String]`       | `None` (no password) |
| `zipMethod` | `ZipMethod` (archiver) | `Deflated`           |
| `chunkSize` | `Int` (unarchiver)     | 64 KiB               |

`Zip4JUnarchiver` uses the password to decrypt encrypted entries.

## Entry Metadata

The entries that `Zip4JUnarchiver` returns include the name, the uncompressed size, and the `lastModified` timestamp, when the archive records them. An entry whose name ends in `/` is a directory.

## Writing Encrypted Archives

`Zip4JArchiver.encrypted(...)` makes a zip archiver that writes encrypted ZIP archives and also allows you to set the encryption method and zip method.

```scala
import zio.compress._
import zio.compress.zip4j._
import zio.stream._

val content = "Hello, ZIO!".getBytes("UTF-8")

val entry = ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(content.length.toLong)

val archive = ZStream((entry, ZStream.fromIterable(content)))
                .via(Zip4JArchiver.encrypted("password", EncryptionMethod.AES_256).archive)
```

## Notes

- The unarchiver's error type is `IOException` for the entry content, and `Throwable` for the archive structure.
- Use [Zip](./reference-zip.md) for archives without a password. It accepts entries whose size is not known in advance.
