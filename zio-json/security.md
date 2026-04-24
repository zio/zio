# Security

> A [Denial of Service](https://en.wikipedia.org/wiki/Denial-of-service_attack) (DOS) attack is a cyber-attack in which the perpetrator seeks to make a machine or network resource unavailable to its intended users by temporarily or indefinitely disrupting services. The vast majority of public-facing servers written in Scala are vulnerable to DOS attack.

A [Denial of Service](https://en.wikipedia.org/wiki/Denial-of-service_attack) (DOS) attack is a cyber-attack in which the perpetrator seeks to make a machine or network resource unavailable to its intended users by temporarily or indefinitely disrupting services. The vast majority of public-facing servers written in Scala are vulnerable to DOS attack.

Attacks that are in the form of a valid payload are designed to be stealthy and will produce the same end-result as a legitimate payload, but will consume more resources along the way. In this section we investigate specific attacks and how `zio-json` mitigates against them.

### Resource Attack: Larger Payload

An obvious way to slow down a server is to give it more data to read. JSON is particularly susceptible to this kind of attack because it has an in-built mechanism to expand the size of the message without altering the contents: pad with whitespace.

Many web frameworks will fully consume the contents of a payload into a `String` before handing it off to the JSON library, so if we receive a JSON message consisting of 1GB of whitespace, we will consume 1GB of heap on that server.

The best way to mitigate against message size attacks is to cap the `Content-Length` to a reasonable size for the use case. A further mitigation is to use a streaming parser that does not require the entire message to be read into memory before parsing begins.

For all the remaining attacks, we will cap the malicious message size to 100KB (the original message is 25KB) and compare the attacks against this baseline. The benchmark results for the original (unedited) payload are given in parentheses, and we can immediately see a reduction in the ops/sec for all frameworks, accompanied by a reduction in memory usage.

<!-- zioJsonJVM/jmh:run -prof gc GoogleMaps.*Attack0 -->

```
       ops/sec        MB/sec
zio    10104 (14823)  1047 (1537)
circe   7456 ( 8832)  1533 (1816)
play    3589 ( 5756)  1344 (2260)
```

### Redundant Data

Most JSON libraries (but not `zio-json`) first create a representation of the JSON message in an Abstract Syntax Tree (AST) that represents all the objects, arrays and values in a generic way. Their decoders typically read what they need from the AST.

An intermediate AST enables attack vectors that insert redundant data, for example in our Google Maps dataset we can add a new field called `redundant` at top-level containing a 60K `String`. If we do this, and run the benchmarks, we see that Circe is heavily impacted, with a 75% reduction in capacity and an increase in memory usage. Play is also impacted, although not as severely. `zio-json`'s ops/sec are reduced but the memory usage is in line which means that throughput is unlikely to be affected by this kind of attack.

<!-- zioJsonJVM/jmh:run -prof gc GoogleMaps.*Attack1 -->

```
       ops/sec       MB/sec
zio    5999 (10104)   622 (1047)
circe  2224 ( 7456)  1655 (1533)
play   2350 ( 3589)  1854 (1344)
```

The reason why `zio-json` is not as badly affected is because it skips values that are unexpected. We can completely mitigate this kind of attack by using the `@jsonNoExtraFields` annotation which results in the payload being rejected at a rate of 5.5 million ops/sec.

Other kinds of redundant values attacks are also possible, such as using an array of 60K full of high precision decimal numbers that require slow parsing (also known as ["near halfway numbers"](https://www.exploringbinary.com/17-digits-gets-you-there-once-youve-found-your-way/)), attacking the CPU. However, the memory attack afforded to us by a redundant `String` is already quite effective.

### `hashCode` Collisions

Following on from the redundant data attack, we can place redundant data in the location of object fields.

JSON libraries that use an intermediate AST often store JSON objects as a stringy `HashMap` (circe uses a [`java.util.LinkedHashMap`](https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/util/LinkedHashMap.html)). If we insert redundant fields that have hashcode collisions with legitimate fields, we can successfully attack both memory and CPU. We need to know the hashing algorithm that is being used, which often falls down to some version of the default Java `String.hashCode` [which is very easy to exploit](https://github.com/plokhotnyuk/jsoniter-scala/blob/master/jsoniter-scala-benchmark/shared/src/main/scala/com/github/plokhotnyuk/jsoniter_scala/benchmark/HashCodeCollider.scala).

In this malicious payload, we add redundant fields that have hashcode collisions, up to 4 collisions per field; we could add more if we used a bruteforce search.

<!-- zioJsonJVM/jmh:run -prof gc GoogleMaps.*Attack2 -->

Again, `zio-json` completely mitigates this attack if the `@jsonNoExtraFields` annotation is used. Note that even if Circe and Play rejected payloads of this nature, it would be too late because the attack happens at the AST layer, not the decoders. However, for the sake of comparison, let's turn off the `zio-json` mitigation:

```
       ops/sec       MB/sec
zio    3742 (10104)   695 (1047)
circe  1992 ( 7456)  1162 (1533)
play   1312 ( 3589)  1636 (1344)
```

ops/sec is down for all decoders relative to the baseline, but since `zio-json` and Circe memory usage is also reduced the throughput on a server might not be impacted as badly as it sounds.

However, this attack hurts Play very badly; memory usage is up compared to the baseline with throughput reduced to 40% of the baseline (22% of the original).

There is a variant of this attack that can be devastating for libraries that rely on `HashMap`. In this attack, [developed by plokhotnyuk to DOS ujson](https://github.com/plokhotnyuk/jsoniter-scala/pull/325), an object is filled with many fields that have a `hashCode` of zero. This exploits two facts:

- Java `String` does not cache `hashCode` of zero, recomputing every time it is requested
- many `HashMap` implementations re-request the hashes of all objects as the number of entries increases during construction.

### Death by a Thousand Zeros

Another kind of attack is to provide data that will cause the decoder for a specific value to do more work than it needs to. Numbers are always a great example of this.

The most brutal attack of this nature is to trick a deserialization library into constructing a gigantic number as a `BigDecimal` and then to downcast to a `BigInteger`. The JVM will happily attempt to reserve GBs of heap for the conversion. Try this in your REPL:

```scala
new java.math.BigDecimal("1e214748364").toBigInteger
```

Fortunately, this kind of attack is prevented by all the mainstream libraries. But it is possible to perform a much weaker form of the attack on Circe, which (to its credit) goes to great effort to pre-validate numbers.

The Google Maps schema has fields of type `Int` and Circe supports the conversion of floating point numbers to integers if the fractional part is zero: so we can pad an integer with as many zeros as possible.

```
       ops/sec       MB/sec
circe  4529 ( 7456)  2037 (1533)
```

This attack is very effective in schemas with lots of numbers, causing ops/sec to be halved with a 33% increase in memory usage.

`zio-json` is resistant to a wide range of number based attacks because it uses a from-scratch number parser that will exit early when the number of bits of any number exceeds 256 bits.
