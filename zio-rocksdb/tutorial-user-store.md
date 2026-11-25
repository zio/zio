# Tutorial: Building a User Store

> Build a small user store step by step: typed values, a column family, a full scan and an atomic credit transfer with ZIO RocksDB.

In this tutorial we build a small store that keeps users and their credit balances in RocksDB. We start with the data type and its encoding, then open a database with a column family, add and read users, move credits between two users atomically, and finally list all users. Each step builds on the previous one, and the last section assembles the application.

We assume that you have read [Getting Started](./getting-started.md).

## Step 1: Model the Data

A user has a name and a number of credits:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import org.rocksdb.{ ColumnFamilyDescriptor, ColumnFamilyHandle, Options }
import zio._
import zio.rocksdb._
import zio.stream.ZStream

case class User(name: String, credits: Long)
```

RocksDB stores bytes, so we need a `Serde[Any, User]`. The encoder writes the name with a length prefix, followed by the credits. The decoder reads them back in the same order. [Serialization](./serialization.md) explains each combinator:

```scala
val nameSerializer: Serializer[Any, String] =
  Serializer[Any, String] { s =>
    for {
      body   <- Serializer.string(s)
      length <- Serializer.int(body.length)
    } yield length ++ body
  }

val nameDeserializer: Deserializer[Any, String] =
  Deserializer.int.flatMap { length =>
    Deserializer[Any, Bytes] { bytes =>
      val (head, tail) = bytes.splitAt(length)
      if (head.length < length) ZIO.fail(DeserializeError.TooShort(bytes.length, length))
      else ZIO.succeed(Result(head, tail))
    }.map(body => new String(body.toArray, UTF_8))
  }

val userSerde: Serde[Any, User] =
  Serde(
    nameSerializer.divide(Serializer.long).contramap(u => (u.name, u.credits)),
    nameDeserializer.zip(Deserializer.long).map { case (name, credits) => User(name, credits) }
  )

val idSerde: Serde[Any, Long] = Serde(Serializer.long, Deserializer.long)
```

Two small helpers convert between a typed value and the `Array[Byte]` that the database expects. A decoding failure becomes a `Throwable`, so that it fits the `Task` of the database operations:

```scala
def encode[A](serde: Serde[Any, A], value: A): UIO[Array[Byte]] =
  serde(value).map(_.toArray)

def decode[A](serde: Serde[Any, A], bytes: Array[Byte]): Task[A] =
  serde.decode(Chunk.fromArray(bytes)).mapBoth(error => new RuntimeException(error.message), _.value)
```

## Step 2: Open the Database

Credit transfers need a transaction, so we open a `TransactionDB`. It extends `RocksDB`, so everything we do with a plain database works on it too. Each run gets a fresh temporary directory, which keeps the tutorial repeatable:

```scala
val database: ZLayer[Any, Throwable, TransactionDB] =
  ZLayer.scoped {
    for {
      path <- ZIO.attempt(Files.createTempDirectory("zio-rocksdb-tutorial").toString)
      db   <- TransactionDB.Live.open(new Options().setCreateIfMissing(true), path)
    } yield db
  }
```

We keep the users in their own column family, so that other data can share the database without key collisions. Creating the family returns its handle, which every later call receives:

```scala
val usersFamily: ZIO[TransactionDB, Throwable, ColumnFamilyHandle] =
  TransactionDB.createColumnFamily(new ColumnFamilyDescriptor("users".getBytes(UTF_8)))
```

## Step 3: Add and Read Users

Both functions encode the id and the user, then call the per-family overloads of `TransactionDB.put` and `TransactionDB.get`:

```scala
def addUser(users: ColumnFamilyHandle, id: Long, user: User): ZIO[TransactionDB, Throwable, Unit] =
  for {
    key   <- encode(idSerde, id)
    value <- encode(userSerde, user)
    _     <- TransactionDB.put(users, key, value)
  } yield ()

def findUser(users: ColumnFamilyHandle, id: Long): ZIO[TransactionDB, Throwable, Option[User]] =
  for {
    key   <- encode(idSerde, id)
    found <- TransactionDB.get(users, key)
    user  <- ZIO.foreach(found)(decode(userSerde, _))
  } yield user
```

## Step 4: Transfer Credits Atomically

Moving credits changes two keys, and a failure in between must not lose credits. `TransactionDB.atomically` runs the whole function in one transaction. `Transaction.getForUpdate` locks each user until the transaction ends, so two concurrent transfers cannot overwrite each other. If the sender has too few credits, the function fails and nothing is written:

```scala
def loadForUpdate(users: ColumnFamilyHandle, id: Long): ZIO[Transaction, Throwable, User] =
  for {
    key   <- encode(idSerde, id)
    found <- Transaction.getForUpdate(users, key, exclusive = true)
    bytes <- ZIO.fromOption(found).orElseFail(new NoSuchElementException(s"no user with id $id"))
    user  <- decode(userSerde, bytes)
  } yield user

def save(users: ColumnFamilyHandle, id: Long, user: User): ZIO[Transaction, Throwable, Unit] =
  for {
    key   <- encode(idSerde, id)
    value <- encode(userSerde, user)
    _     <- Transaction.put(users, key, value)
  } yield ()

def transfer(users: ColumnFamilyHandle, from: Long, to: Long, credits: Long): ZIO[TransactionDB, Throwable, Unit] =
  TransactionDB.atomically[Throwable, Unit] {
    for {
      sender   <- loadForUpdate(users, from)
      receiver <- loadForUpdate(users, to)
      _        <- ZIO.fail(new IllegalStateException("not enough credits")).when(sender.credits < credits)
      _        <- save(users, from, sender.copy(credits = sender.credits - credits))
      _        <- save(users, to, receiver.copy(credits = receiver.credits + credits))
    } yield ()
  }
```

See [Transactions](./transactions.md) for the locking rules and [How to Retry on a Lock Timeout](./how-to-retry-on-lock-timeout.md) for conflicts under load.

## Step 5: List All Users

`TransactionDB.newIterator` with a handle scans one column family as a stream. We decode every key and value as they arrive:

```scala
def allUsers(users: ColumnFamilyHandle): ZStream[TransactionDB, Throwable, (Long, User)] =
  TransactionDB.newIterator(users).mapZIO { case (key, value) =>
    decode(idSerde, key).zip(decode(userSerde, value))
  }
```

Keys are non-negative big-endian `Long` values here, so the scan returns the users in id order.

## The Complete Application

To assemble the steps into a runnable application, we create the family, add two users, transfer credits and print every user:

```scala
object UserStore extends ZIOAppDefault {
  val program: ZIO[TransactionDB, Throwable, Unit] =
    for {
      users <- usersFamily
      _     <- addUser(users, 1L, User("Ada", 100L))
      _     <- addUser(users, 2L, User("Grace", 50L))
      _     <- transfer(users, from = 1L, to = 2L, credits = 30L)
      _     <- allUsers(users).runForeach(Console.printLine(_))
    } yield ()

  def run = program.provide(database)
}
```

Running it prints `(1,User(Ada,70))` and `(2,User(Grace,80))`.

## Where to Go Next

- [How to Scan a Prefix](./how-to-scan-a-prefix.md) shows how to read only the entries whose key starts with a given prefix.
- [How to Test with a Temporary Database](./how-to-test-with-a-temporary-database.md) shows how to run tests against a real database in a temporary directory.
- The [reference pages](./reference-rocksdb.md) list every operation used here.
