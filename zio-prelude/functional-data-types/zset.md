# ZSet

> `ZSet` is a version of a set that generalizes the measure of "how many times" each element appears in the set.

`ZSet` is a version of a set that generalizes the measure of "how many times" each element appears in the set.

Conceptually, we can think of a `ZSet[A, B]` as a `Map[A, B]`, where `A` represents the elements of the set and `B` represents some notion of how many times each element appears.

We can then define a `Set` as a `ZSet` where the measure of how many times each element appears is a `Boolean`.

```scala

type Set[+A] = ZSet[A, Boolean]
```

That is, in a `Set` the measure of how many times an element appears is just yes or no. Either the element is in the set or it is not.

However, we can also define many other variants of sets by choosing different types to measure how many times an element appears.

For example, by using the `Natural` new type from ZIO Prelude, which describes integers greater than or equal to zero, we can define the type of a `MultiSet` or "bag" in some programming languages.

```scala

type MultiSet[+A] = ZSet[A, Natural]
```

This ends up being a quite useful data type and can represent anything where the number of times an element appears matters but the order doesn't matter, such as a shopping cart.

By using other measures of how many times an item appears we can describe other variants of sets such as `Int` for sets where items can appear a negative number of times or `Double` for sets where the number of times an item appears can be fractional.

`ZSet` is focused on using the functional abstractions and new types in ZIO Prelude to describe operators on these generalizations of sets in as practical a way as possible.

## Constructing ZSets

The most basic way to construct a `ZSet` is from a `Map` where the keys in the map represent the elements in the `ZSet` and the values in the map represent the number of times each element appears.

```scala
val shoppingList: ZSet[String, Int] =
  ZSet.fromMap(Map("apples" -> 2, "oranges" -> 3, "bananas" -> 5))
// shoppingList: ZSet[String, Int] = ZSet(apples -> 2, bananas -> 5, oranges -> 3)
```

We can also construct a `ZSet` from existing collection types.

If we have a normal `Set` from the Scala standard library, we can convert it to a `ZSet` using the `fromSet` operator.

```scala
val set: ZSet[Int, Boolean] =
  ZSet.fromSet(Set(1, 2, 3))
// set: ZSet[Int, Boolean] = ZSet(1 -> true, 2 -> true, 3 -> true)
```

If we have an `Iterable` we can convert it into a `ZSet` using the `fromIterable` constructor. The measure of how many times each item appears will just be how many times it appears in the `Iterable`.

```scala
val multiSet: MultiSet[Int] =
 ZSet.fromIterable(List(1, 1, 2))
// multiSet: MultiSet[Int] = ZSet(1 -> 2, 2 -> 1)
```

Finally, we can just construct an empty `ZSet` using the `empty` operator and combine `ZSet` with other `ZSet` values later.

```scala
val empty: ZSet[Nothing, Nothing] =
  ZSet.empty
// empty: ZSet[Nothing, Nothing] = ZSet()
```

## Operators on ZSets

As a generalization of sets, `ZSet` supports many of the same operators that exist on sets.

### Set Membership

We can determine how many times an element appears in the set using the `apply` operator. This requires that the measure type has an `Identity` defined for it.

If the element exists in the set the measure of how many times it appears will be returned. Otherwise the identity element will be returned.

Thus, `shoppingList("apples")` will return `2` and `shoppingList("eggs")` will return `0` while `set(1)` will return `true` and `set(4)` will return `false`.

### Set Union

One simple way of combining sets is the union of two sets.

The union of two sets contains all elements that appear in either set. If an element appears in both sets the measure of how many times it appears in the union will be the maximum of the two measures, as determined by an `Ord` defined on the measure type.

```scala
val left: ZSet[Int, Int] =
  ZSet.fromMap(Map(1 -> 2, 2 -> 3, 3 -> 4))
// left: ZSet[Int, Int] = ZSet(1 -> 2, 2 -> 3, 3 -> 4)

val right: ZSet[Int, Int] =
  ZSet.fromMap(Map(2 -> 2, 3 -> 3, 4 -> 4))
// right: ZSet[Int, Int] = ZSet(2 -> 2, 3 -> 3, 4 -> 4)

val union: ZSet[Int, Int] =
  left.union(right)
// union: ZSet[Int, Int] = ZSet(1 -> 2, 2 -> 3, 3 -> 4, 4 -> 4)
```

Here the result will be `ZSet(1 -> 2, 2 -> 3, 3 -> 4, 4 -> 4)` because all elements in both sets will be included in the union of the two sets.

## Set Intersection

Set intersection is another basic way of combining sets.

The intersection of two sets only contains the elements that appear in both sets. If an element appears in both sets the measure of how many times it appears in the intersection will be the minimum of the two measures, as determined by an `Ord` defined on the measure type.

```scala
val left: ZSet[Int, Int] =
  ZSet.fromMap(Map(1 -> 2, 2 -> 3, 3 -> 4))
// left: ZSet[Int, Int] = ZSet(1 -> 2, 2 -> 3, 3 -> 4)

val right: ZSet[Int, Int] =
  ZSet.fromMap(Map(2 -> 2, 3 -> 3, 4 -> 4))
// right: ZSet[Int, Int] = ZSet(2 -> 2, 3 -> 3, 4 -> 4)

val intersection: ZSet[Int, Int] =
  left.intersect(right)
// intersection: ZSet[Int, Int] = ZSet(2 -> 2, 3 -> 3)
```

The result will be `ZSet(2 -> 2, 3 -> 3)` because the only commonality of the two sets is that they contain these elements.

## Set Difference

We can also compute the difference of two sets.

The difference of two sets is obtained by subtracting the measure of how many times each element appears in the second set from how many times it appears in the first set. This requires an `Inverse` defined on the `Sum` of the measure type to tell us what it means to "subtract" for this type.

```scala
val left: ZSet[Int, Int] =
  ZSet.fromMap(Map(1 -> 2, 2 -> 3, 3 -> 4))
// left: ZSet[Int, Int] = ZSet(1 -> 2, 2 -> 3, 3 -> 4)

val right: ZSet[Int, Int] =
  ZSet.fromMap(Map(2 -> 2, 3 -> 3, 4 -> 4))
// right: ZSet[Int, Int] = ZSet(2 -> 2, 3 -> 3, 4 -> 4)

val diff: ZSet[Int, Int] =
  left.diff(right)
// diff: ZSet[Int, Int] = ZSet(1 -> 2, 2 -> 1, 3 -> 1, 4 -> -4)
```

The result will be `ZSet(1 -> 2, 2 -> 1, 3 -> 1, 4 -> -4)`.

Note that the measure of how many times `4` appears in the resulting set is negative. This is permissible because `Int` allows negative numbers.

If we didn't want this we could define the measure type to be a type like `Natural` that did not permit negative numbers.

```scala
val left: MultiSet[String] =
  ZSet.fromIterable(List("apple", "banana"))
// left: MultiSet[String] = ZSet(banana -> 1, apple -> 1)

val right: MultiSet[String] =
  ZSet.fromIterable(List("banana", "banana", "banana"))
// right: MultiSet[String] = ZSet(banana -> 3)

val diff: MultiSet[String] =
  left.diff(right)
// diff: MultiSet[String] = ZSet(banana -> 0, apple -> 1)
```

Now the measure of how many times each item appears is a natural number so "banana" cannot appear a negative number of times.

## Set Combination

Another helpful way of combining sets that makes more sense for generalized sets is the notion of "adding" two sets. This is less relevant for traditional sets because an element can never appear more than once in a traditional set, so "adding" reduces to set union.

However, for generalized sets this way of combining can be quite intuitive. For example if we think of a multiset as John's shopping list and another multiset as Jane's shopping list, the combination of the two includes everything on both shopping lists, with overlapping items appearing the total number of times they appear on both lists.

This requires a `Commutative` defined on the `Sum` of the measure type to tell us how to do this addition.

```scala
val janeShoppingList: ZSet[String, Int] =
  ZSet.fromMap(Map("apples" -> 4, "bananas" -> 5))
// janeShoppingList: ZSet[String, Int] = ZSet(apples -> 4, bananas -> 5)

val johnShoppingList: ZSet[String, Int] =
  ZSet.fromMap(Map("eggs" -> 12, "bananas" -> 1))
// johnShoppingList: ZSet[String, Int] = ZSet(eggs -> 12, bananas -> 1)

val combinedList: ZSet[String, Int] =
  janeShoppingList.combine(johnShoppingList)
// combinedList: ZSet[String, Int] = ZSet(eggs -> 12, apples -> 4, bananas -> 6)
```

The result is `ZSet("apples" -> 4, "bananas" -> 6, "eggs" -> 12)`, the combination of what each of them wants to buy.

## Set Product

We can also define the product of two sets.

This is the Cartesian product of the two sets, with each combination of elements from the two sets appearing in the product set and the measure of how many times an element appears being the product of the two measures. This requires a `Commutative` defined on the `Prod` of the measure type to tell us how to do this multiplication.

A nice example of this is modeling discrete probabilities. We can define the set of all possible outcomes of a dice roll along with their associated probabilities like this:

```scala
val die: ZSet[Int, Double] =
  ZSet.fromMap(
    Map(
      1 -> 1.0 / 6.0,
      2 -> 1.0 / 6.0,
      3 -> 1.0 / 6.0,
      4 -> 1.0 / 6.0,
      5 -> 1.0 / 6.0,
      6 -> 1.0 / 6.0
    )
  )
// die: ZSet[Int, Double] = ZSet(5 -> 0.16666666666666666, 1 -> 0.16666666666666666, 6 -> 0.16666666666666666, 2 -> 0.16666666666666666, 3 -> 0.16666666666666666, 4 -> 0.16666666666666666)
```

The product of this set with itself represents the set of all possible outcomes of rolling two dice.

```scala
val pair: ZSet[(Int, Int), Double] =
  die.zip(die)
// pair: ZSet[(Int, Int), Double] = ZSet((5,4) -> 0.027777777777777776, (2,6) -> 0.027777777777777776, (4,1) -> 0.027777777777777776, (5,2) -> 0.027777777777777776, (1,1) -> 0.027777777777777776, (1,6) -> 0.027777777777777776, (6,4) -> 0.027777777777777776, (3,2) -> 0.027777777777777776, (3,3) -> 0.027777777777777776, (4,3) -> 0.027777777777777776, (5,6) -> 0.027777777777777776, (2,2) -> 0.027777777777777776, (3,4) -> 0.027777777777777776, (2,1) -> 0.027777777777777776, (6,1) -> 0.027777777777777776, (2,5) -> 0.027777777777777776, (4,2) -> 0.027777777777777776, (4,4) -> 0.027777777777777776, (1,2) -> 0.027777777777777776, (6,5) -> 0.027777777777777776, (6,2) -> 0.027777777777777776, (1,4) -> 0.027777777777777776, (4,5) -> 0.027777777777777776, (4,6) -> 0.027777777777777776, (2,4) -> 0.027777777777777776, (6,3) -> 0.027777777777777776, (1,5) -> 0.027777777777777776, (3,1) -> 0.027777777777777776, (5,1) -> 0.027777777777777776, (3,6) -> 0.027777777777777776, (2,3) -> 0.027777777777777776, (3,5) -> 0.027777777777777776, (1,3) -> 0.027777777777777776, (5,5) -> 0.027777777777777776, (5,3) -> 0.027777777777777776, (6,6) -> 0.027777777777777776)
```

This set has thirty six elements, corresponding to each possible combination of outcomes from the two dice rolls, each with a probability of one in thirty six.

This gets more interesting if we say that matters is not the individual dice rolls but their sum.

We can express this in code with the `zipWith` operator, which allows us to combine the elements from the product of the two sets with a function. If multiple elements from the product are mapped to the same value the measures of how many times each value appears will be added, using a `Commutative` instance defined on the `Sum` of the measure type.

```scala
val sum: ZSet[Int, Double] =
  die.zipWith(die)(_ + _)
// sum: ZSet[Int, Double] = ZSet(5 -> 0.1111111111111111, 10 -> 0.08333333333333333, 6 -> 0.1388888888888889, 9 -> 0.1111111111111111, 2 -> 0.027777777777777776, 12 -> 0.027777777777777776, 7 -> 0.16666666666666669, 3 -> 0.05555555555555555, 11 -> 0.05555555555555555, 8 -> 0.1388888888888889, 4 -> 0.08333333333333333)
```

The resulting set has eleven elements, corresponding to the possible sums from two dice rolls of two to twelve. The measure for each value corresponds to the probability of that sum being rolled.

So for example the probability of seven is about 16.7% whereas the probability of rolling two or twelve is only about 2.8%.

We can take this even further using the `flatMap` operator.

The `zipWith` operator lets us combine two sets when the sets are independent of each other. With the `flatMap` operator we can create a new set based on each element of the first set and then "flatten" the resulting set down.

We could rewrite the example above using `flatMap` and a for comprehension like this:

```scala
val sum: ZSet[Int, Double] =
  for {
    a <- die
    b <- die
  } yield a + b
// sum: ZSet[Int, Double] = ZSet(5 -> 0.1111111111111111, 10 -> 0.08333333333333333, 6 -> 0.1388888888888889, 9 -> 0.1111111111111111, 2 -> 0.027777777777777776, 12 -> 0.027777777777777776, 7 -> 0.16666666666666669, 3 -> 0.05555555555555555, 11 -> 0.05555555555555555, 8 -> 0.1388888888888889, 4 -> 0.08333333333333333)
```

This allows us to express conditional logic, such as the probability distribution of outcomes for a subsequent event depending on the outcome of a previous event.

## Transforming Elements

We can transform set elements using the `map` operator.

For example, we could add one to each value in a set like this:

```scala
val setPlusOne: ZSet[Int, Boolean] =
  set.map(_ + 1)
// setPlusOne: ZSet[Int, Boolean] = ZSet(2 -> true, 3 -> true, 4 -> true)
```

This requires a `Commutative` instance defined for the `Sum` of the measure type so that we can combine the measures if we map two elements to the same value.

## Transforming Measures

We can also transform the measure we use to represent how many times an item appears using the `transform` operator. For example, perhaps we have constructed a set where the measure is an `Int` but we would like to transform it to a `Double` so we can compose it with other `ZSet` values with `Double` as the measure type.

We can do that with `transform` like this:

```scala
def constant(n: Int): ZSet[Int, Double] =
  ZSet(n).transform(_.toDouble)
```

## Extracting ZSet Values

If the number of times a value appears in the set is a natural number then we can use normal collection operators to fold over its values, treating it much like a normal set

```scala
val fruits: MultiSet[String] =
  ZSet.fromIterable(List("apple", "banana", "orange", "apple", "orange"))
// fruits: MultiSet[String] = ZSet(banana -> 1, orange -> 2, apple -> 2)

val fruitCount: Int =
  fruits.foldLeft(0)((n, _) => n + 1)
// fruitCount: Int = 5
```

This will return `5` because there are five elements in the set we are folding over.

We can convert a `ZSet` back to a `Map` from elements to a measure of how many times they appear using the `toMap` operator.

We can also always convert a `ZSet` back to a `Set` using the `toSet` operator. Any elements in the `ZSet` with a measure equal to zero based on an `Identity` defined on the `Sum` of the measure type will be discarded from the resulting set.
