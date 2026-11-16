# How to Read a Password-Protected ZIP

> Some ZIP archives are protected by a password. The Zip4J module reads these archives, and the standard `zip` module does not. This guide shows how to list and read the entries of such an archive.

## Read the Entries

Pass the password to `Zip4JUnarchiver`. The reader uses the password to decrypt each entry as it reads it:

```scala
import zio._
import zio.compress._
import zio.stream._

val listProtected: ZIO[Any, Throwable, Chunk[String]] =
  ZStream
    .fromFileName("secret.zip")
    .via(Zip4JUnarchiver(password = Some("it is a secret")).list)
    .map(entry => s"${entry.name} (${entry.uncompressedSize.getOrElse(-1L)} bytes)")
    .runCollect
```

To read the content of each entry, use `unarchive` and read each entry's content before the next entry is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md) for the rules. The example below prints the size of each entry's content:

```scala
import zio._
import zio.compress._
import zio.stream._

val readProtected: ZIO[Any, Throwable, Unit] =
  ZStream
    .fromFileName("secret.zip")
    .via(Zip4JUnarchiver(password = Some("it is a secret")).unarchive)
    .mapZIO { case (entry, content) =>
      content.runCollect.flatMap { bytes =>
        Console.printLine(s"${entry.name}: ${bytes.size} bytes")
      }
    }
    .runDrain
```

## Use the Right Password

The password is a `String`, and Zip4J converts it to characters when it opens the archive. Keep the password out of the source code. Read it from an environment variable or a secret store, for example:

```scala
import zio._

val password: UIO[Option[String]] = ZIO.succeed(sys.env.get("ARCHIVE_PASSWORD"))
```

## Notes

- The module can read archives that use the traditional ZIP encryption, which the `zip -e` command produces.
- An archive without a password can be read with `Zip4JUnarchiver()`, or with `ZipUnarchiver` from the `zip` module.
- Writing a password-protected archive does not encrypt the entries yet. See the warning on the [Zip4J](./reference-zip4j.md) reference page.
