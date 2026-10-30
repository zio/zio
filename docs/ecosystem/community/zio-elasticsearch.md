---
id: zio-elasticsearch
title: "ZIO Elasticsearch"
---

[ZIO Elasticsearch](https://lambdaworks.github.io/zio-elasticsearch) is a type-safe and streaming-friendly ZIO native Elasticsearch client.

## Introduction

The library uses sttp as the HTTP client for executing requests, and builds on other ZIO libraries: ZIO Schema for type-safe queries and ZIO Prelude newtypes for `IndexName`, `DocumentId`, and `Routing`.

Key features of ZIO Elasticsearch:
- **Type Safety** — Build queries and aggregations with accessors derived from a ZIO Schema, so referring to a field that doesn't exist is a compile error.
- **Fluent API** — Add optional parameters, such as routing and refresh, in chained method calls.
- **Bulk Requests** — Combine requests like `create`, `upsert`, and `deleteById` into a single `bulk` request.
- **Streaming** — Stream search results with a plain, scroll, or search-after strategy.

The supported versions are Scala 2.12, 2.13, and 3, ZIO 2, Elasticsearch 7 and 8, and JVM 17 or later.

## Installation

In order to use this library, we need to add the following line to our `build.sbt` file:

```scala
libraryDependencies += "io.lambdaworks" %% "zio-elasticsearch" % "0.5.1"
```

## Example

To execute a request, we use the `Elasticsearch` layer, which has an `execute` method accepting an `ElasticRequest`. The layer needs an `ElasticExecutor` (`ElasticExecutor.local` runs against `localhost:9200`, while `ElasticExecutor.live` needs an `ElasticConfig`) and an sttp backend:

```scala
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.elasticsearch._
import zio._

object ZIOElasticsearchExample extends ZIOAppDefault {
  val indexName = IndexName("index")
  val result: RIO[Elasticsearch, CreationOutcome] =
    Elasticsearch.execute(ElasticRequest.createIndex(indexName))

  override def run =
    result.provide(
      ElasticExecutor.local,
      Elasticsearch.layer,
      HttpClientZioBackend.layer()
    )
}
```

## Type-safe Queries

To make requests type-safe, we derive a `Schema` for our document type and use it to create accessors:

```scala
final case class Address(street: String, number: Int)

object Address {
  implicit val schema: Schema.CaseClass2[String, Int, Address] =
    DeriveSchema.gen[Address]

  val (street, number) = schema.makeAccessors(FieldAccessorBuilder)
}

final case class User(id: Int, address: Address)

object User {
  implicit val schema: Schema.CaseClass2[String, Address, User] =
    DeriveSchema.gen[User]

  val (id, address) = schema.makeAccessors(FieldAccessorBuilder)
}

val query: BoolQuery[User] =
  ElasticQuery
    .must(ElasticQuery.range(User.id).gte(7).lt(10))
    .should(ElasticQuery.startsWith(User.address / Address.street, "ZIO"))

val request: SearchRequest =
  ElasticRequest.search(IndexName("index"), query)

val result: RIO[Elasticsearch, SearchResult] = Elasticsearch.execute(request)
```

## Streaming

The `Elasticsearch.stream` and `Elasticsearch.streamAs` methods turn a search request into a `ZStream`. The `StreamConfig` selects the strategy:

```scala
val request: SearchRequest =
  ElasticRequest.search(IndexName("index"), ElasticQuery.range(User.id).gte(5))

val scrollStream: ZStream[Elasticsearch, Throwable, Item] =
  Elasticsearch.stream(request, StreamConfig.Scroll)

val searchAfterStream: ZStream[Elasticsearch, Throwable, User] =
  Elasticsearch.streamAs[User](request, StreamConfig.SearchAfter)
```
