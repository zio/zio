# FtpResource

> `FtpResource` is the metadata of one file, directory or link on a server. The listing operations `FtpAccessors#ls` and `FtpAccessors#lsDescendant` stream `FtpResource` values, and `FtpAccessors#stat` returns one when the path exists.

## Definition

`FtpResource` is a case class with five fields:

```scala
final case class FtpResource(
  path: String,
  size: Long,
  lastModified: java.time.Instant,
  permissions: Set[java.nio.file.attribute.PosixFilePermission],
  isDirectory: Option[Boolean]
)
```

The fields have these meanings:

- `path` is the location of the entry on the server, such as `/reports/2026-11.csv`.
- `size` is the size in bytes, as the server reports it. Directory sizes are server-specific.
- `lastModified` is the last modification time.
- `permissions` is the set of Unix permission bits the server reports for owner, group and others.
- `isDirectory` is `Some(true)` for a directory, `Some(false)` for anything else, and `None` when the type is unknown.

## What Each Protocol Fills In

The values come from different sources depending on the connection, so their precision differs:

| Field          | FTP and FTPS                                                                            | SFTP                                                           | StubFtp                                            |
| -------------- | --------------------------------------------------------------------------------------- | -------------------------------------------------------------- | -------------------------------------------------- |
| `path`         | `ls`: listed directory joined with the entry name; `stat`: the name in the `MLST` reply | `ls`: path of the directory entry; `stat`: the path you passed | path relative to the stub root, with a leading `/` |
| `lastModified` | listing timestamp (often minute precision for `ls`)                                     | modification time in seconds                                   | file system modification time                      |
| `permissions`  | parsed from the listing                                                                 | from the file attributes                                       | POSIX permissions, empty on Windows                |
| `isDirectory`  | always `Some`                                                                           | `Some` from `ls`, `None` from `stat`                           | always `Some`                                      |

On FTP, `FtpAccessors#ls` and `FtpAccessors#lsDescendant` parse the server's `LIST` output, which often shows only the minute for recent files and only the day for older ones. `FtpAccessors#stat` uses `MLST`, which reports seconds. On SFTP, `FtpAccessors#stat` does not determine the entry type, so check `isDirectory` with `Option#contains` (`isDirectory.contains(true)` or `isDirectory.contains(false)`) rather than `Option#get`.

## Working with Resources

`FtpResource` is a plain case class, so you can create one directly, which is handy in tests:

```scala
import zio.ftp._
import java.nio.file.attribute.PosixFilePermission._
import java.time.Instant

val report = FtpResource(
  path = "/reports/2026-11.csv",
  size = 2048L,
  lastModified = Instant.parse("2026-11-01T08:00:00Z"),
  permissions = Set(OWNER_READ, OWNER_WRITE, GROUP_READ),
  isDirectory = Some(false)
)
// report: FtpResource = FtpResource(
//   path = "/reports/2026-11.csv",
//   size = 2048L,
//   lastModified = 2026-11-01T08:00:00Z,
//   permissions = Set(OWNER_READ, OWNER_WRITE, GROUP_READ),
//   isDirectory = Some(value = false)
// )
```

To render the permissions in the familiar `rwxr-xr-x` form, convert the set with `java.nio.file.attribute.PosixFilePermissions.toString`:

```scala
import java.nio.file.attribute.PosixFilePermissions
import scala.jdk.CollectionConverters._

PosixFilePermissions.toString(report.permissions.asJava)
// res0: String = "rw-r-----"
```

To get the file name without its directory:

```scala
report.path.split('/').last
// res1: String = "2026-11.csv"
```

## Constructors from Java Clients

The companion object also converts the Java clients' metadata types. The FTP and SFTP implementations use them internally, and they are public for code that calls the Java clients through `FtpAccessors#execute`:

```scala
object FtpResource {
  def fromFtpFile(f: org.apache.commons.net.ftp.FTPFile, path: Option[String] = None): FtpResource
  def fromResource(file: net.schmizz.sshj.sftp.RemoteResourceInfo): FtpResource
  def apply(path: String, attr: net.schmizz.sshj.sftp.FileAttributes): FtpResource
}
```

`FtpResource.fromFtpFile` uses the file name as the path, prefixed with `path` and a `/` when `path` is given (a `path` of `/` gives `/name`, not `//name`). `FtpResource.fromResource` converts an sshj directory entry, and `FtpResource.apply(path, attr)` converts sshj file attributes, leaving `isDirectory` as `None`. For example, to list a directory with the `MLSD` command, which reports exact timestamps:

```scala
import zio._
import zio.ftp._

val exactListing: ZIO[Ftp, java.io.IOException, List[FtpResource]] =
  Ftp.execute(_.mlistDir("/reports").toList.map(FtpResource.fromFtpFile(_, Some("/reports"))))
```

## Related Pages

The operations that produce `FtpResource` values are documented in the [Ftp API](ftp-accessors.md).
