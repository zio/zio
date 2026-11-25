# Concepts

> Explanations of how ZIO JSON works and why it behaves the way it does: its design, Scala 2 and Scala 3 differences, decoder strictness, JSON shapes, performance and security.

These pages explain how ZIO JSON works and why it behaves the way it does. Read them to understand a behaviour or a trade-off. For exact signatures and options, see the [Reference](../reference/index.md); for step-by-step tasks, see the [How-to Guides](../how-to/index.md).

- [How ZIO JSON Works](how-it-works.md): the design that makes it fast and safe, and how derivation finds instances.
- [Scala 2 and Scala 3 Differences](scala-2-vs-scala-3.md): every behaviour that differs between the two versions, and a migration checklist.
- [How Strict Is the Decoder](decoder-strictness.md): what the decoders accept beyond strict JSON, and how to add stricter checks.
- [JSON Shapes and Round Trips](wire-format.md): how each kind of sum-type case is written, and which values do not survive an encode then decode.
- [Performance](performance.md): benchmarks and tips for fast encoding and decoding.
- [Security](security.md): how ZIO JSON handles hostile input, and what you still need to limit yourself.
