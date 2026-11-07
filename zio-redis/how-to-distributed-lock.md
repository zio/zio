# How to Build a Distributed Lock

> This guide builds a lock that several processes can use to take turns on a shared resource. It uses `Redis#set` with the "only if absent" option to acquire the lock and a Lua script to release it safely.

## Acquire the Lock

To take the lock, set a key only when it does not exist yet, and give it an expiry so that a crashed holder cannot block everyone forever. `Update.SetNew` maps to the `NX` option:

```scala
import zio._
import zio.redis._

def acquire(redis: Redis, name: String, owner: String, ttl: Duration): IO[RedisError, Boolean] =
  redis.set(s"lock:$name", owner, Some(ttl), Some(Update.SetNew))
```

The result is `true` for the one caller that got the lock and `false` for everyone else. The `owner` value must be unique per holder, for example a random UUID.

## Release the Lock

A holder must delete the key only if it is still the owner. If the lock expired and another process took it, a plain `Redis#del` would release the other process's lock. A Lua script performs the check and the delete atomically. `Redis#eval` needs an `Input` instance for the keys and arguments and an `Output` instance for the result, and the library does not provide implicit ones, so we declare an implicit `Input[String]` from `Input.StringInput` and an implicit `Output[Long]` from `Output.LongOutput`:

```scala
import zio._
import zio.redis._
import zio.redis.Input._
import zio.redis.Output._

implicit val stringInput: Input[String] = StringInput
implicit val longOutput: Output[Long]   = LongOutput

val releaseScript: String =
  """if redis.call('get', KEYS[1]) == ARGV[1] then
    |  return redis.call('del', KEYS[1])
    |else
    |  return 0
    |end""".stripMargin

def release(redis: Redis, name: String, owner: String): IO[RedisError, Boolean] =
  redis
    .eval(releaseScript, Chunk(s"lock:$name"), Chunk(owner))
    .returning[Long]
    .map(_ == 1L)
```

See [Scripting](./commands/scripting.md) for the full set of script commands.

## Run an Effect Under the Lock

To make sure the lock is always released, wrap the protected effect in `ZIO.acquireReleaseWith`. This version fails with `None` when the lock is already held:

```scala
import zio._
import zio.redis._
import zio.redis.Input._
import zio.redis.Output._

def withLock[R, A](redis: Redis, name: String, ttl: Duration)(
  effect: ZIO[R, RedisError, A]
): ZIO[R, RedisError, Option[A]] = {
  implicit val stringInput: Input[String] = StringInput
  implicit val longOutput: Output[Long]   = LongOutput

  val release =
    """if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"""

  ZIO.randomWith(_.nextUUID).flatMap { token =>
    val owner = token.toString
    ZIO.acquireReleaseWith(
      redis.set(s"lock:$name", owner, Some(ttl), Some(Update.SetNew))
    )(acquired =>
      ZIO
        .when(acquired)(redis.eval(release, Chunk(s"lock:$name"), Chunk(owner)).returning[Long])
        .ignore
    ) {
      case true  => effect.map(Some(_))
      case false => ZIO.none
    }
  }
}
```

Choose `ttl` longer than the longest the protected effect can run. A lock that expires early can be held by two processes at once, so this lock suits efficiency use cases such as avoiding duplicate work, not strict correctness guarantees.
