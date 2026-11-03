# Decrel

> [Decrel](https://yoohaemin.github.io/decrel) is a Scala library for **dec**larative programming using **rel**ations between our data. It fetches data with automatic batching, parallelization, and caching while keeping the business logic clean and readable. It runs on ZIO through ZIO Query, and on Cats Effect through Fetch.

## Introduction

Fetching data from datasources is usually done by calling repository methods one after another. This works, but it hides a few issues: independent fetches run sequentially, fetching many items leads to the N+1 query problem, and caching needs extra code for every operation.

With Decrel, we:
- **Declare Relations** — Express the relationships between our data models as first-class values, for example "a `Book` has one `Author`" or "a `User` may have a `PremiumSubscription`".
- **Implement Datasources** — Decide how each relation is fulfilled, including batching and caching.
- **Compose Relations** — Combine simple relations sequentially (`<>:`) or in parallel (`&`) to express complex access patterns.
- **Execute Efficiently** — The composed relations run with batching and parallelism by default, through ZIO Query.
- **Generate Test Data** — The same relations can generate random data for ZIO Test and ScalaCheck.

## Installation

In order to use this library with ZIO, we need to add the following lines to our `build.sbt` file:

```scala
libraryDependencies += "com.yoohaemin" %% "decrel-zquery" % "0.2.0"
libraryDependencies += "com.yoohaemin" %% "decrel-ziotest" % "0.2.0" % Test
```

The `decrel-fetch` module is for Cats Effect users, and `decrel-scalacheck` generates test data for ScalaCheck.

## Example

First, we declare the relations between the data models:

```scala
object Book {
  object author extends Relation.Single[Book, Author]
}

object User {
  object subscription extends Relation.Optional[User, PremiumSubscription]
}
```

Then we implement the datasource of each relation. The implementation receives all the inputs at once, so it can batch them:

```scala
implementSingleDatasource(Book.author) { books =>
  ZIO.succeed(books.map(book => book -> authorMap(book.authorId)))
}
```

Finally, we compose the relations and run them. The author and the price are fetched in parallel:

```scala
val bookId: Book.Id = ???

for {
  (book: Book, author: Author, price: Price) <-
    (Book.Self & Book.author & Book.price).toZIO(bookId)
} yield ()
```

To fetch the details of many books, we use `toZIOMany`. The underlying calls are batched, so the number of calls to each datasource doesn't grow with the number of books:

```scala
val bookIds: List[Book.Id] = ???

for {
  bookDetails: List[(Book, Author, Price)] <-
    (Book.Self & Book.author & Book.price).toZIOMany(bookIds)
} yield ()
```

For the full documentation, see the [Decrel documentation](https://yoohaemin.github.io/decrel).
