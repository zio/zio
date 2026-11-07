# Scripting

> Redis can run Lua scripts on the server. A script runs atomically, so no other command executes while it runs. This page covers the seven scripting commands on `Redis`.

The library has no `MULTI`, `EXEC`, `DISCARD` or `WATCH` commands and no transaction API, so atomic multi-step logic is written as a Lua script and run with `Redis#eval` or `Redis#evalSha`.

## Running Scripts

`Redis#eval` sends the script text, a `Chunk` of keys that the script reads through `KEYS`, and a `Chunk` of arguments that it reads through `ARGV`. The following table lists the commands that run scripts:

| Command          | Purpose                                                  |
| ---------------- | -------------------------------------------------------- |
| `Redis#eval`     | Runs a script sent as text                               |
| `Redis#evalSha`  | Runs a script already cached on the server, by its SHA1  |

Both commands finish with `returning[R]`, but unlike the other commands they do not use a ZIO Schema `Schema`. They require an `Input[K]` for the keys, an `Input[A]` for the arguments and an `Output[R]` for the result, and the library provides no implicit instances of these. We define them ourselves from the public building blocks in `Input` and `Output`, such as `StringInput`, `LongInput`, `LongOutput`, `MultiStringOutput` and `ChunkOutput`.

The following example defines the instances in an object and increments a counter only when it is below a limit. The script runs atomically, so concurrent callers cannot exceed the limit:

```scala
import zio._
import zio.redis._

object ScriptCodecs {
  implicit val stringInput: Input[String] = Input.StringInput
  implicit val longInput: Input[Long]     = Input.LongInput
  implicit val longOutput: Output[Long]   = Output.LongOutput
}

import ScriptCodecs._

val incrementBelowLimit: String =
  """local current = tonumber(redis.call('GET', KEYS[1]) or '0')
    |if current < tonumber(ARGV[1]) then
    |  return redis.call('INCR', KEYS[1])
    |end
    |return -1""".stripMargin

def increment(redis: Redis, limit: Long): IO[RedisError, Long] =
  redis.eval(incrementBelowLimit, Chunk("counter"), Chunk(limit)).returning[Long]
```

The script's return value is converted by the `Output` you choose, so it must match the Lua type: a number needs `LongOutput`, a string `MultiStringOutput`, and a table `ChunkOutput` of an element output. A mismatch fails with a `RedisError`.

## Caching Scripts

Sending the whole script on every call wastes bandwidth. `Redis#scriptLoad` stores the script in the server's script cache and returns its SHA1 digest, which `Redis#evalSha` accepts in place of the text. The following example loads a script once and then runs it by digest:

```scala
import zio._
import zio.redis._

object ShaCodecs {
  implicit val stringInput: Input[String]  = Input.StringInput
  implicit val stringOutput: Output[String] = Output.MultiStringOutput
}

import ShaCodecs._

def runCached(redis: Redis): IO[RedisError, String] =
  for {
    sha    <- redis.scriptLoad("return ARGV[1]")
    result <- redis.evalSha(sha, Chunk.empty[String], Chunk("hello")).returning[String]
  } yield result
```

The cache is not persistent. After a server restart or `Redis#scriptFlush`, `Redis#evalSha` fails with a `NOSCRIPT` error, so a program that relies on it should load the script again and retry.

## Managing the Script Cache

The following table lists the commands that manage the cache and running scripts:

| Command                | Parameters                          | Returns          |
| ---------------------- | ----------------------------------- | ---------------- |
| `Redis#scriptLoad`     | `script: String`                    | The SHA1 digest  |
| `Redis#scriptExists`   | `sha1: String, sha1s: String*`      | `Chunk[Boolean]` |
| `Redis#scriptFlush`    | `mode: Option[FlushMode] = None`    | `Unit`           |
| `Redis#scriptKill`     | none                                | `Unit`           |
| `Redis#scriptDebug`    | `mode: DebugMode`                   | `Unit`           |

`Redis#scriptExists` returns one `Boolean` per digest, in the order given. `Redis#scriptKill` stops the running script, and Redis refuses when the script has already written data. The following example flushes the cache only when a script is missing:

```scala
import zio._
import zio.redis._

def flushIfMissing(redis: Redis, sha: String): IO[RedisError, Unit] =
  redis.scriptExists(sha).flatMap { found =>
    ZIO.unless(found.forall(identity))(redis.scriptFlush(Some(FlushMode.Async))).unit
  }
```

## Option Types

`FlushMode` selects how `Redis#scriptFlush` clears the cache. When no mode is given, the server uses its default, which is synchronous:

| Value             | Behavior                              |
| ----------------- | ------------------------------------- |
| `FlushMode.Async` | Flushes the cache in the background   |
| `FlushMode.Sync`  | Flushes the cache and waits           |

`DebugMode` configures the Lua debugger for scripts run afterwards with `Redis#eval`:

| Value            | Behavior                                                       |
| ---------------- | -------------------------------------------------------------- |
| `DebugMode.Yes`  | Non-blocking debugging; changes made by the script are discarded |
| `DebugMode.Sync` | Blocking debugging; changes made by the script are saved       |
| `DebugMode.No`   | Turns debugging off                                            |

The following example turns the debugger off:

```scala
import zio._
import zio.redis._

def stopDebugging(redis: Redis): IO[RedisError, Unit] =
  redis.scriptDebug(DebugMode.No)
```

## Related Pages

- [Keys](./keys.md) and [Strings](./strings.md) cover the commands a script usually calls.
- [Error Handling](../error-handling.md) lists the errors raised by failing scripts.
