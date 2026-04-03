---
id: zio-test-diff
title: "zio.test.diff.Diff"
sidebar_label: "zio.test.diff.Diff"
---

```scala mdoc:invisible
import zio.test.diff.Diff
```

When asserting two things are the same it's sometimes difficult to see the difference. Luckily there is a `zio.test.Diff` type-class. The purpose this type class is to output the difference between two things.

This can be one of the primitives types like `String`, `Int`, `Double`, etc. But also more complex structures like a `Map`, `List` and so-forth.

### Derive for case classes and algebraic data types

To _derive_ a type-class for a case class or a algebraic data type you can include the module `zio-test-magnolia` if it's not included already. Which includes `DeriveDiff` and `DeriveGen` as well.

To make it work you need to import the `DeriveDiff` object/trait:

```scala mdoc:silent
import zio.test.magnolia.DeriveDiff._
```

An example of a difference output inside a test may look like this

```
     ✗ There was a difference
        Expected
        Person(
          name = "Bibi",
          nickname = Some("""Bibbo
          The
          Bibber
          Bobber"""),
          age = 300,
          pet = Pet(
            name = "The Beautiful Destroyer",
            hasBone = false,
            favoriteFoods = List("Alpha", "This is a wonderful way to live and die", "Potato", "Brucee Lee", "Potato", "Ziverge"),
            birthday = 2023-08-20T17:32:33.479852Z
          ),
          person = Some(Person(
            name = "Bibi",
            nickname = Some("""Bibbo
            The
            Bibber
            Bobber"""),
            age = 300,
            pet = Pet(
              name = "The Beautiful Destroyer",
              hasBone = false,
              favoriteFoods = List("Alpha", "This is a wonderful way to live and die", "Potato", "Brucee Lee", "Potato", "Ziverge"),
              birthday = 2023-08-20T17:32:33.479855Z
            ),
            person = None
          ))
        )
        Diff -expected +obtained
        Person(
          name = "Bibi" → "Boboo",
          nickname = Some(
            """Bibbo
            The
            Bibber
            Bobber""" → """Babbo
            The
            Bibber"""
          ),
          pet = Pet(
            name = "The Beautiful Destroyer" → "The Beautiful Crumb",
            favoriteFoods = List(
              1 = "This is a wonderful way to live and die" → "This is a wonderful \"way\" to dance and party",
              3 = "Brucee Lee",
              4 = "Potato",
              5 = "Ziverge"
            ),
            birthday = 2023-08-20T17:32:33.479852Z → -1000000000-01-01T00:00:00Z
          ),
          person = Some(
            Person(
              name = "Bibi" → "Boboo",
              nickname = Some(
                """Bibbo
                The
                Bibber
                Bobber""" → """Babbo
                The
                Bibber"""
              ),
              pet = Pet(
                name = "The Beautiful Destroyer" → "The Beautiful Crumb",
                favoriteFoods = List(
                  1 = "This is a wonderful way to live and die" → "This is a wonderful \"way\" to dance and party",
                  3 = "Brucee Lee",
                  4 = "Potato",
                  5 = "Ziverge"
                ),
                birthday = 2023-08-20T17:32:33.479855Z → -1000000000-01-01T00:00:00Z
              )
            )
          )
        )
      p1 == p2
      p1 = Person(
        name = "Boboo",
        nickname = Some("""Babbo
        The
        Bibber"""),
        age = 300,
        pet = Pet(
          name = "The Beautiful Crumb",
          hasBone = false,
          favoriteFoods = List("Alpha", "This is a wonderful \"way\" to dance and party", "Potato"),
          birthday = -1000000000-01-01T00:00:00Z
        ),
        person = Some(Person(
          name = "Boboo",
          nickname = Some("""Babbo
          The
          Bibber"""),
          age = 300,
          pet = Pet(
            name = "The Beautiful Crumb",
            hasBone = false,
            favoriteFoods = List("Alpha", "This is a wonderful \"way\" to dance and party", "Potato"),
            birthday = -1000000000-01-01T00:00:00Z
          ),
          person = None
        ))
      )
```

### Custom types

For more custom types you could provide type-class instances your self by implementing the `zio.test.diff.Diff` type-class.

```scala mdoc:silent
// somewhere defined in your domain package
case class Percentage(repr: Int)

implicit val diffPercentage: Diff[Percentage] = Diff[Double].contramap(_.repr)
```

### Be wary of `LowPriDiff`

One thing to note that there is a trait `LowPriDiff` which is stacked on the companion object of `zio.test.diff.Diff`. There is lower priority type-class instance defined at `LowerPriDiff` which is a fallback for `AnyVal`. It's defined as `implicit def anyValDiff[A <: AnyVal]: Diff[A] = anyDiff[A]`, so if some custom types mess up your diff, you might want to check on this topic. 