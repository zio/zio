# Geo

> The geo commands store longitude and latitude pairs in a sorted set and answer distance and radius queries over them. This page documents the eight geo commands and the option types they accept.

All commands are methods on the `Redis` service. Keys and members are encoded with the configured [`CodecSupplier`](../codecs.md). See [Getting Started](../getting-started.md) for how to build the layer.

## Commands

The commands are grouped below by purpose:

| Group     | Method                         | Redis Command                   | Result                |
| --------- | ------------------------------ | ------------------------------- | --------------------- |
| Add       | `Redis#geoAdd`                 | `GEOADD`                        | `Long`                |
| Inspect   | `Redis#geoDist`                | `GEODIST`                       | `Option[Double]`      |
| Inspect   | `Redis#geoHash`                | `GEOHASH`                       | `Chunk[Option[String]]` |
| Inspect   | `Redis#geoPos`                 | `GEOPOS`                        | `Chunk[Option[LongLat]]` |
| Search    | `Redis#geoRadius`              | `GEOSEARCH` (`FROMLONLAT`)      | `Chunk[GeoView]`      |
| Search    | `Redis#geoRadiusByMember`      | `GEOSEARCH` (`FROMMEMBER`)      | `Chunk[GeoView]`      |
| Store     | `Redis#geoRadiusStore`         | `GEOSEARCHSTORE` (`FROMLONLAT`) | `Long`                |
| Store     | `Redis#geoRadiusByMemberStore` | `GEOSEARCHSTORE` (`FROMMEMBER`) | `Long`                |

The search commands are sent as `GEOSEARCH` and `GEOSEARCHSTORE`, so they require Redis 6.2 or later. None of the commands uses `returning`, because the member name is always read back as a `String`.

## Option Types

The following types are available after `import zio.redis._`:

| Type                  | Shape                                                                          | Used by                                  |
| --------------------- | ------------------------------------------------------------------------------ | ---------------------------------------- |
| `LongLat`             | `LongLat(longitude: Double, latitude: Double)`                                 | `geoAdd`, `geoPos`, `geoRadius` and more |
| `RadiusUnit`          | `Meters`, `Kilometers`, `Feet` or `Miles`                                      | `geoDist` and every radius command       |
| `WithCoord`           | Singleton `WithCoord`, adds the position to each result                        | `geoRadius`, `geoRadiusByMember`         |
| `WithDist`            | Singleton `WithDist`, adds the distance from the center to each result         | `geoRadius`, `geoRadiusByMember`         |
| `WithHash`            | Singleton `WithHash`, adds the raw geohash integer to each result              | `geoRadius`, `geoRadiusByMember`         |
| `Count`               | `Count(count: Long)`                                                           | Radius commands                          |
| `Order`               | `Order.Ascending` or `Order.Descending`                                        | Radius commands                          |
| `Store`               | `Store(key: String)`, the destination key                                      | `StoreResults`, `StoreBoth`              |
| `StoreDist`           | `StoreDist(key: String)`                                                       | `StoreDistances`, `StoreBoth`            |
| `StoreOptions`        | `StoreResults`, `StoreDistances` or `StoreBoth`                                | `geoRadiusStore`, `geoRadiusByMemberStore` |
| `GeoView`             | `GeoView(member, dist, hash, longLat)` where the last three are `Option`s      | Result of the search commands            |

A `GeoView` only has the optional fields that were requested with `WithDist`, `WithHash` and `WithCoord`. The others are `None`.

## Adding Members

`Redis#geoAdd` takes a key and at least one pair of a `LongLat` and a member. It returns the number of new members added. The `LongLat` is built with the longitude first, then the latitude:

```scala
import zio._
import zio.redis._

def addCities(redis: Redis): IO[RedisError, Long] =
  redis.geoAdd(
    "cities",
    (LongLat(13.361389, 38.115556), "Palermo"),
    (LongLat(15.087269, 37.502669), "Catania")
  )
```

## Inspecting Members

`Redis#geoDist` returns the distance between two members, or `None` if either one is missing. The unit defaults to meters when `radiusUnit` is `None`:

```scala
import zio._
import zio.redis._

def distance(redis: Redis): IO[RedisError, Option[Double]] =
  redis.geoDist("cities", "Palermo", "Catania", Some(RadiusUnit.Kilometers))
```

`Redis#geoPos` and `Redis#geoHash` return one entry per requested member, with `None` for members that do not exist:

```scala
import zio._
import zio.redis._

def positions(redis: Redis): IO[RedisError, (Chunk[Option[LongLat]], Chunk[Option[String]])] =
  for {
    pos    <- redis.geoPos("cities", "Palermo", "Catania")
    hashes <- redis.geoHash("cities", "Palermo", "Catania")
  } yield (pos, hashes)
```

## Searching by Radius

`Redis#geoRadius` finds members within a radius of a coordinate, and `Redis#geoRadiusByMember` does the same around an existing member. Both accept the optional `withCoord`, `withDist`, `withHash`, `count` and `order` parameters, and return a `Chunk[GeoView]`:

```scala
import zio._
import zio.redis._

def nearby(redis: Redis): IO[RedisError, Chunk[GeoView]] =
  redis.geoRadius(
    "cities",
    LongLat(15.0, 37.0),
    200.0,
    RadiusUnit.Kilometers,
    withDist = Some(WithDist),
    count = Some(Count(5)),
    order = Some(Order.Ascending)
  )
```

To search around a member that is already in the key, pass the member instead of a coordinate. Here the position is also returned:

```scala
import zio._
import zio.redis._

def aroundPalermo(redis: Redis): IO[RedisError, Chunk[(String, Option[LongLat])]] =
  redis
    .geoRadiusByMember("cities", "Palermo", 200.0, RadiusUnit.Kilometers, withCoord = Some(WithCoord))
    .map(_.map(view => (view.member, view.longLat)))
```

## Storing Search Results

`Redis#geoRadiusStore` and `Redis#geoRadiusByMemberStore` write the matches into another key and return how many members were stored. They take a `StoreOptions` value instead of the `with*` flags. Use `StoreResults` to store the matching members:

```scala
import zio._
import zio.redis._

def storeNearby(redis: Redis): IO[RedisError, Long] =
  redis.geoRadiusStore(
    "cities",
    LongLat(15.0, 37.0),
    200.0,
    RadiusUnit.Kilometers,
    StoreResults(Store("cities:nearby")),
    count = Some(Count(10))
  )
```

The `StoreDistances` variant stores the distances in a second key with `STOREDIST`, and `StoreBoth` stores the members and the distances in two keys. To store both, pass a `Store` and a `StoreDist`:

```scala
import zio._
import zio.redis._

def storeNearbyWithDistances(redis: Redis): IO[RedisError, Long] =
  redis.geoRadiusStore(
    "cities",
    LongLat(15.0, 37.0),
    200.0,
    RadiusUnit.Kilometers,
    StoreBoth(Store("cities:nearby"), StoreDist("cities:distances")),
    count = Some(Count(10))
  )
```

## Next Steps

- [Sorted Sets](./sorted-sets.md) explains the structure that geo members are stored in, so commands such as `Redis#zRem` and `Redis#zCard` work on geo keys.
- [Keys](./keys.md) covers expiry and deletion.
