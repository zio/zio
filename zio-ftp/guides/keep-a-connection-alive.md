# Keep a Connection Alive

> Stop firewalls and NAT gateways from dropping idle FTP connections with TCP keepalive, control-channel keepalive, timeouts and short-lived connections.

In this guide, we keep FTP connections from being dropped by firewalls and NAT gateways, make dead connections fail quickly instead of hanging, and structure long-running programs so an idle connection can't break them.

## The Problem

Firewalls and NAT gateways forget TCP connections that have been idle for a while, often after a few minutes. When that happens to an FTP connection, nothing fails right away: the next command waits for a reply that never comes. FTP makes this worse, because a large transfer moves its bytes over the data connection while the control connection sits idle; the transfer finishes, but the server's confirmation is lost, and the download fails with `FileTransferIncompleteError` after all the data has arrived.

A program that opens one connection at startup and then sleeps between runs runs straight into it:

```scala
import org.apache.commons.net.ftp.FTPClient

val client = new FTPClient()
client.connect("ftp.example.com", 21)
client.login("one", "1234")
while (true) {
  Thread.sleep(60 * 60 * 1000L)   // the firewall forgets the idle connection here
  client.listFiles("/incoming")   // waits for a reply that never comes
}
```

Four tools help, and ZIO FTP turns the first two on by default: TCP keepalive and socket timeouts, followed by `NOOP` commands during uploads and connections that don't live longer than they need to.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md). The examples share these imports:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.io.IOException
```

## Tuning TCP Keepalive

TCP keepalive makes the operating system send small probe packets on an idle connection. The probes keep NAT entries fresh and detect a dead peer. `UnsecureFtpSettings` turns it on by default with `KeepaliveSettings.default`: the first probe after 60 idle seconds, then one every 10 seconds, giving up after 5 unanswered probes. If a gateway drops connections sooner than 60 seconds, lower `idle`:

```scala
val settings = UnsecureFtpSettings("ftp.example.com", 21, PasswordCredentials("one", "1234")).copy(
  keepalive = Some(KeepaliveSettings(idle = Some(30), interval = Some(10), count = Some(3)))
)
```

The values are in seconds and apply to every socket the client opens. A `None` value keeps the operating system's default for that parameter. If the platform doesn't support one of the options, ZIO FTP logs a warning and connects without it.

## Failing Fast on a Dead Connection

Keepalive notices a dead peer only after several probes. The two timeouts bound how long a single read can wait, so a lost connection fails instead of hanging:

```scala
val boundedSettings = settings.copy(
  defaultTimeout = Some(30.seconds),
  dataTimeout = Some(2.minutes)
)
```

The `defaultTimeout` field is the read timeout of the control connection: it bounds how long the client waits for a command reply. It doesn't limit how long the initial TCP connection attempt can take. The `dataTimeout` field covers each read on a data connection, so set it longer than the server's slowest pause during a transfer. Both default to one minute. A read that times out fails with `java.net.SocketTimeoutException`, an `IOException`.

## Keeping the Control Connection Busy During Uploads

TCP keepalive keeps the idle control connection open at the network level, but some firewalls also track FTP sessions and close a control connection that sends no commands. Commons Net can send a `NOOP` command over the control connection while an upload is in progress. ZIO FTP doesn't expose this setting, so turn it on through `Ftp.execute` once after connecting:

```scala
val uploadWithNoops: ZIO[Ftp, IOException, Unit] =
  Ftp.execute(_.setControlKeepAliveTimeout(5.minutes)) *>
    Ftp.upload("/backups/archive.tar", ZStream.fromPath(java.nio.file.Paths.get("archive.tar")))
```

With this setting, the client sends a `NOOP` whenever five minutes pass during an upload. It applies to `Ftp.upload`, which uses `FTPClient#storeFile`; `Ftp.readFile` streams the data itself, so it doesn't send `NOOP`s and relies on TCP keepalive.

## Using Short-Lived Connections

The most robust fix for idle connections is not to keep them. A program that wakes up every hour to sync files doesn't need a connection in between. Open one per run with `UnsecureFtp.connect` and `ZIO.scoped`, so every run starts with a fresh connection and closes it when done:

```scala
def syncOnce(ftp: FtpAccessors[Any]): ZIO[Any, IOException, Unit] =
  ftp.ls("/incoming").runCount.flatMap(n => Console.printLine(s"$n files waiting").orDie)

val hourlySync: ZIO[Any, IOException, Long] =
  ZIO
    .scoped(UnsecureFtp.connect(boundedSettings).flatMap(syncOnce))
    .retry(Schedule.recurs(3) && Schedule.spaced(30.seconds))
    .repeat(Schedule.fixed(1.hour))
```

The retry reconnects on every attempt, because each attempt runs `UnsecureFtp.connect` again. Compare this with providing `unsecure(settings)` once for the whole program: that connection lives as long as the program, which is right for a burst of work and wrong for a job that sleeps between runs.

## SFTP Connections

`SecureFtpSettings` has no dedicated keepalive or timeout fields; an SSH connection carries its own traffic over a single TCP connection and is less prone to the idle control connection problem, but a NAT gateway can still drop it when idle. For SFTP jobs that sleep between runs, use short-lived connections with `SecureFtp.connect`, reusing `syncOnce`:

```scala
val sftpSync: ZIO[Any, IOException, Unit] =
  ZIO.scoped(
    SecureFtp
      .connect(SecureFtpSettings("sftp.example.com", 22, PasswordCredentials("one", "1234")))
      .flatMap(syncOnce)
  )
```

## Going Further

For every timeout and keepalive field, see [FTP and FTPS settings](../reference/unsecure-ftp-settings.md#timeouts-and-keepalive). For scoped connections, see [Connecting](../reference/connecting.md#scoped-connections).
