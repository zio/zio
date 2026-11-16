# Zip

> The `zip` module creates and reads ZIP archives. It is built on the JVM standard library's ZIP support. For password-protected archives, use the [Zip4J](./reference-zip4j.md) module instead. Also use the Zip4J module when you need to use the 'Stored' zip-method and the archive entries are large, as this module needs to pull the full content of each entry in memory to calculate a checksum.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "3.0.0"
```

## ZipArchiver

`ZipArchiver` is an `Archiver` that writes ZIP archives. Its entries do not need a known size, so you can stream content of unknown length. Use `ZipArchiver.apply` to set the compression level and method, or `ZipArchiver.archive` for the defaults:

```scala
import zio.compress._
import zio.stream._

val files = ZStream.fromIterable(
  Seq(
    ArchiveEntry[Option, Any]("notes/readme.txt") -> ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")),
    ArchiveEntry[Option, Any]("notes", isDirectory = true) -> ZStream.empty,
  )
)

val archive: ZStream[Any, Throwable, Byte] = files.via(ZipArchiver().archive)
```

Set `isDirectory` to `true` to write a directory entry; the writer adds the trailing `/` to the name. A name that ends in `/` without `isDirectory` is written as a file, with the slash removed.

## ZipUnarchiver

`ZipUnarchiver` is an `Unarchiver` that reads ZIP archives. Each entry comes with its content as a stream:

```scala
import zio.compress._
import zio.stream._

val entries: ZStream[Any, Throwable, (String, Long)] =
  ZStream
    .fromFileName("archive.zip")
    .via(ZipUnarchiver().list)
    .map(entry => entry.name -> entry.uncompressedSize.getOrElse(-1L))
```

`ZipUnarchiver#list` reads each entry's content to fill in the uncompressed size. To process the content itself, use `ZipUnarchiver#unarchive` instead, and read each entry's content before the next entry is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md) for the rules.

## Options

| Option      | Type                                         | Default                          |
|-------------|----------------------------------------------|----------------------------------|
| `level`     | `Option[DeflateCompressionLevel]` (archiver) | `None` (zlib's default, level 6) |
| `zipMethod` | `ZipMethod` (archiver)                       | `Deflated`                       |
| `chunkSize` | `Int` (unarchiver)                           | 64 KiB                           |

`level` applies only to deflated entries. `ZipMethod` has two values: `ZipMethod.Deflated` compresses each entry, and `ZipMethod.Stored` writes each entry without compression.

## Stored Entries

`ZipMethod.Stored` only works in v3.0.0 and later, earlier versions produced broken zip files.

Zip support from the JVM requires a CRC32 to be calculated up front. To do this, the full content of each entry is pulled into memory. The Zip4J module does not do this, and will have lower memory usage.

## Notes

- The archive format has no encryption in this module. Use [Zip4J](./reference-zip4j.md) to read password-protected archives.
- The archiver does not check the size of each entry, because the size is optional for this format.
- The unarchiver's error type is `IOException` for the entry content, and `Throwable` for the archive structure.
