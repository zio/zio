# Security

> How ZIO JSON behaves under hostile input: large payloads, redundant data, hash collisions, expensive numbers, deep nesting and huge strings, and the mitigations available to you.

A [Denial of Service](https://en.wikipedia.org/wiki/Denial-of-service_attack) (DoS) attack is a cyber-attack in which the perpetrator seeks to make a machine or network resource unavailable to its intended users by temporarily or indefinitely disrupting services. Any public-facing server that decodes JSON from its clients is a potential target.

Attacks that are in the form of a valid payload are designed to be stealthy and produce the same end result as a legitimate payload, but consume more resources along the way. This page goes through specific attacks, how ZIO JSON behaves under each one, and what you can do about it. The last section, [Mitigations](#mitigations), sums up the defences.

:::note
The benchmark tables on this page come from the first version of ZIO JSON, published in July 2020. They compare ZIO JSON with Circe and Play JSON on the hardware and library versions of that time. The Play JSON benchmarks have since been removed from the suite, so the tables cannot be reproduced as they are. The `…Attack…` benchmarks in `GoogleMapsAPIBenchmarks` still exist for ZIO JSON, Circe and jsoniter-scala; see [Performance](performance.md#the-benchmark-suite) for how to run them.
:::

## Resource Attack: Larger Payload

An obvious way to slow down a server is to give it more data to read. JSON is particularly susceptible to this kind of attack because it has a built-in mechanism to expand the size of the message without altering the contents: pad with whitespace. ZIO JSON skips whitespace without any limit. In our test, 20 million spaces followed by `1` decode as the `Int` `1`.

Many web frameworks fully consume the payload into a `String` before handing it to the JSON library, so a JSON message consisting of 1 GB of whitespace takes 1 GB of heap on that server, plus a copy if the body was read as bytes first.

The best way to mitigate message size attacks is to cap the request size (the `Content-Length`) at a reasonable value for the use case, in the HTTP server or proxy. Two further mitigations come from ZIO JSON itself:

- Decode the body from bytes. `JsonDecoder#decodeJson` and `fromJson` accept a `Chunk[Byte]` or an `Array[Byte]` and decode the UTF-8 while parsing, so the payload is not copied into a `String` first. See [`decodeJson`](../reference/json-decoder.md#decodejson).
- On the JVM, decode from a stream with `JsonDecoder#decodeJsonStreamInput`, which reads the input incrementally instead of loading the whole message into memory. See [Streaming API](../reference/streaming.md).

<!-- zioJsonJVM/Jmh/run -prof gc GoogleMaps.*Attack0 -->

For all the remaining attacks, we cap the malicious message size to 100 KB (the original message is 25 KB) and compare the attacks against this baseline. The benchmark results for the original (unedited) payload are given in parentheses. The ops/sec drop for all libraries, along with the memory usage:

```
       ops/sec        MB/sec
zio    10104 (14823)  1047 (1537)
circe   7456 ( 8832)  1533 (1816)
play    3589 ( 5756)  1344 (2260)
```

## Redundant Data

Most JSON libraries (but not ZIO JSON) first create a representation of the JSON message in an Abstract Syntax Tree (AST) that represents all the objects, arrays and values in a generic way. Their decoders typically read what they need from the AST.

<!-- zioJsonJVM/Jmh/run -prof gc GoogleMaps.*Attack1 -->

An intermediate AST enables attack vectors that insert redundant data. For example, in our Google Maps dataset we can add a new field called `redundant` at the top level, containing a 60K `String`. With this payload, Circe is heavily impacted, with a 70% reduction in capacity and an increase in memory usage. Play is also impacted, although not as severely. ZIO JSON's ops/sec are reduced, but its memory usage stays in line, so throughput is unlikely to be affected by this kind of attack:

```
       ops/sec       MB/sec
zio    5999 (10104)   622 (1047)
circe  2224 ( 7456)  1655 (1533)
play   2350 ( 3589)  1854 (1344)
```

ZIO JSON is not as badly affected because a derived decoder skips the values of fields it does not know. Skipping does not build anything, and it is not recursive, so even an unknown field nested 100,000 levels deep is skipped without trouble. Skipping also does not validate the skipped value fully, so some malformed values inside unknown fields are accepted. We define a small type to show it:

```scala
import zio.json._
import zio.json.ast.Json

final case class Name(name: String)

object Name {
  implicit val decoder: JsonDecoder[Name] = DeriveJsonDecoder.gen[Name]
}
```

The value `tzzz` is not valid JSON, but it sits in an unknown field, so decoding succeeds:

```scala
"""{"name":"Ada","x":tzzz}""".fromJson[Name]
// res1: Either[String, Name] = Right(value = Name(name = "Ada"))
```

We can completely mitigate this kind of attack with the `@jsonNoExtraFields` annotation, or with `allowExtraFields = false` in a [`JsonCodecConfiguration`](../reference/json-codec-configuration.md#allowextrafields). The decoder then rejects the payload at the first unknown field, at a rate of 5.5 million ops/sec in the 2020 benchmarks:

```scala
@jsonNoExtraFields
final case class StrictName(name: String)

object StrictName {
  implicit val decoder: JsonDecoder[StrictName] = DeriveJsonDecoder.gen[StrictName]
}

"""{"name":"Ada","x":1}""".fromJson[StrictName]
// res2: Either[String, StrictName] = Left(value = "(invalid extra field)")
```

Other kinds of redundant values attacks are also possible, such as an array of 60K full of high-precision decimal numbers that require slow parsing (also known as ["near halfway numbers"](https://www.exploringbinary.com/17-digits-gets-you-there-once-youve-found-your-way/)), attacking the CPU. However, the memory attack afforded by a redundant `String` is already quite effective.

## `hashCode` Collisions

Following on from the redundant data attack, we can place redundant data in the location of object fields.

JSON libraries that use an intermediate AST often store JSON objects as a `HashMap` keyed by string (Circe uses a [`java.util.LinkedHashMap`](https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/util/LinkedHashMap.html)). If we insert redundant fields that have hash code collisions with legitimate fields, we can successfully attack both memory and CPU. We need to know the hashing algorithm that is being used, which often comes down to some version of the default Java `String.hashCode`, [which is very easy to exploit](https://github.com/plokhotnyuk/jsoniter-scala/blob/master/jsoniter-scala-benchmark/shared/src/main/scala/com/github/plokhotnyuk/jsoniter_scala/benchmark/HashCodeCollider.scala).

In this malicious payload, we add redundant fields that have hash code collisions, up to 4 collisions per field; we could add more with a brute-force search.

<!-- zioJsonJVM/Jmh/run -prof gc GoogleMaps.*Attack2 -->

ZIO JSON completely mitigates this attack if the `@jsonNoExtraFields` annotation is used. Even if Circe and Play rejected payloads of this nature, it would be too late, because the attack happens at the AST layer, not in the decoders. For the sake of comparison, here are the results with the ZIO JSON mitigation turned off:

```
       ops/sec       MB/sec
zio    3742 (10104)   695 (1047)
circe  1992 ( 7456)  1162 (1533)
play   1312 ( 3589)  1636 (1344)
```

ops/sec is down for all decoders relative to the baseline, but since ZIO JSON and Circe memory usage is also reduced, the throughput on a server might not be impacted as badly as it sounds. This attack hurts Play very badly: memory usage is up compared to the baseline, with throughput reduced to about 37% of the baseline (23% of the original).

There is a variant of this attack that can be devastating for libraries that rely on `HashMap`. In this attack, [developed by plokhotnyuk to DoS ujson](https://github.com/plokhotnyuk/jsoniter-scala/pull/325), an object is filled with many fields whose `String#hashCode` is zero. This exploits two facts:

- Before JDK 13, Java `String` did not cache a `String#hashCode` of zero, so it recomputed the hash every time it was requested.
- Many `HashMap` implementations re-request the hashes of all objects as the number of entries grows during construction.

### Decoding Into a `Map`

Derived case class decoders match field names without hashing, and the `Json` AST keeps an object's fields in a `Chunk`, so neither builds a hash map from the payload's keys. Decoding into `Map[String, V]` (or `HashMap`, `mutable.Map` and the other map types) does build a hash map from attacker-chosen keys, so it is open to the same collision attack.

In our test, a 540 KB object with 16,384 keys that all share one `String.hashCode` took about 3 seconds to decode as a `Map[String, Int]`. The same object decoded as `Json` in about 5 ms, and an object of the same size with ordinary keys decoded as a `Map[String, Int]` in about 15 ms.

When the keys of an object come from untrusted input, decode it into a case class if the keys are known in advance. Otherwise decode it into `Json` or with `JsonDecoder.keyValueChunk`, which keeps the key-value pairs in a `Chunk` without hashing (duplicate keys stay as separate entries):

```scala
"""{"a":1,"b":2,"a":3}""".fromJson(JsonDecoder.keyValueChunk[String, Int])
// res3: Either[String, zio.Chunk[(String, Int)]] = Right(
//   value = IndexedSeq(("a", 1), ("b", 2), ("a", 3))
// )
```

## Death by a Thousand Zeros

Another kind of attack is to provide data that makes the decoder for a specific value do more work than it needs to. Numbers are always a great example of this.

The most brutal attack of this nature is to trick a deserialization library into constructing a gigantic number as a `BigDecimal` and then to convert it to a `BigInteger`. The JVM will happily attempt to reserve gigabytes of heap for the conversion. This expression, which is not run here, does exactly that:

```scala
new java.math.BigDecimal("1e214748364").toBigInteger
```

Fortunately, this kind of attack is prevented by all the mainstream libraries. But it is possible to perform a much weaker form of the attack on Circe, which (to its credit) goes to great effort to pre-validate numbers.

The Google Maps schema has fields of type `Int`, and Circe supports the conversion of floating point numbers to integers if the fractional part is zero, so we can pad an integer with as many zeros as possible:

```
       ops/sec       MB/sec
circe  4529 ( 7456)  2037 (1533)
```

This attack is very effective in schemas with lots of numbers, causing a 39% drop in ops/sec with a 33% increase in memory usage.

ZIO JSON is resistant to a wide range of number-based attacks because it uses a from-scratch number parser that exits early. `Int` and `Long` fail as soon as the value overflows. A `BigInt` or `BigInteger` fails when it needs more than 256 bits, and a `BigDecimal` (or a `Json.Num`) fails when its mantissa, the digits without the exponent, needs more than 256 bits. The limit is the constant `Lexer.NumberMaxBits`:

```scala
("1" + "0" * 100).fromJson[BigInt]
// res4: Either[String, BigInt] = Left(value = "(expected a 256-bit BigInt)")

("1." + "1" * 100).fromJson[BigDecimal]
// res5: Either[String, BigDecimal] = Left(
//   value = "(expected a BigDecimal with 256-bit mantissa)"
// )
```

The exponent of a `BigDecimal` is not capped by that limit, only by the range of `Int`. A number such as `1e2000000000` is accepted as a `BigDecimal` or a `Json.Num`, because it is stored as a small mantissa and a scale:

```scala
"1e2000000000".fromJson[BigDecimal]
// res6: Either[String, BigDecimal] = Right(value = 1E+2000000000)
```

ZIO JSON's own conversions from such a value are safe. Converting the `Json.Num` to a `BigInt` with `Json#as` fails at once:

```scala
"1e2000000000".fromJson[Json].flatMap(_.as[BigInt])
// res7: Either[String, BigInt] = Left(value = "(expected a 256-bit BigInt)")
```

Your own code is not protected in the same way. Calling `toBigInt` or `toBigInteger` (or anything that expands the number to all its digits) on a decoded `BigDecimal` from untrusted input reproduces the attack above. Check the size first, for example with `scale` and `precision`, or decode into a bounded type such as `Long` or `Double` when the domain allows it.

## Deeply Nested Input

The decoders are recursive: decoding an array inside an array calls the array decoder again, one level of the JVM call stack per level of nesting. ZIO JSON has no explicit limit on nesting depth. Instead, `JsonDecoder#decodeJson`, `fromJson` and `JsonDecoder#fromJsonAST` catch the `StackOverflowError` that very deep input causes and return the error `Unexpected structure`:

```scala
("[" * 1000000).fromJson[Json]
// res8: Either[String, Json] = Left(value = "Unexpected structure")
```

How deep a document can be before this happens depends on the stack size of the thread that decodes it. In our test on a JVM with default settings, 1,000 nested arrays decoded into `Json` and 10,000 failed with `Unexpected structure`.

Deep nesting only matters where the target type allows it. A case class decoder only descends as deep as the type itself is nested, and unknown fields are skipped without recursion. `Json`, recursive types such as trees, and nested collections follow the input as deep as it goes.

:::warning
The JVM streaming decoders `JsonDecoder#decodeJsonStream` and `JsonDecoder#decodeJsonStreamInput` do not catch `StackOverflowError`. ZIO treats that error as fatal, so it is not a normal failure of the effect. In our test, decoding 100,000 opening brackets into `Json` with `JsonDecoder#decodeJsonStream` stopped a `ZIOAppDefault` application with a "Catastrophic error encountered" report. Do not decode untrusted input into recursive types such as `Json` with these methods.
:::

## Huge Strings

There is no limit on the length of a JSON string or on the number of elements in an array or object. In our test, a single string of 20 million characters decoded successfully. The only bound is the size of the input, which is why a request size limit in the HTTP server matters more than any check in your decoders: a `JsonDecoder#mapOrFail` that rejects long values runs only after the value has been read into memory.

## Mitigations

ZIO JSON avoids many of these attacks by design, but the following choices are yours:

| Risk                                     | Mitigation                                                                                                                   |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Large payloads, whitespace, huge strings | Cap the request size in the HTTP server or proxy.                                                                            |
| Copying the body into a `String`         | Decode from `Chunk[Byte]` or `Array[Byte]`, or, on the JVM, from a stream with `JsonDecoder#decodeJsonStreamInput`.          |
| Redundant data in unknown fields         | Use `@jsonNoExtraFields` or `allowExtraFields = false`.                                                                      |
| Hash collisions in object keys           | Decode into case classes, `Json` or `JsonDecoder.keyValueChunk` instead of `Map[String, V]` when keys come from the client.  |
| Huge `BigDecimal` exponents              | Do not expand untrusted `BigDecimal` values to `BigInt`; check `scale` and `precision`, or use bounded number types.         |
| Deep nesting                             | Prefer non-recursive target types; avoid the JVM streaming decoders for untrusted input into recursive types such as `Json`. |
