# ZIO Schema Use Cases

> ZIO Schema allows us to create representations of our data types as values.

Once we have a representation of our data types, we can use it to:

- Serialize and deserialize our types
- Validate our types ([Validation](operations/validating-types.md))
- Transform our types ([Transforming Schemas](operations/transforming-schemas.md))
- Create default instances of our types ([Getting The Default Value](operations/the-default-value.md))

We can then use one of the available codecs (or [write our own](how-to/custom-codec.md)) to serialize and deserialize our types. ZIO Schema ships these codecs, each in its own module:

- JSON (`zio-schema-json`)
- Protocol Buffers (`zio-schema-protobuf`)
- Apache Avro (`zio-schema-avro`)
- Apache Thrift (`zio-schema-thrift`)
- BSON (`zio-schema-bson`)
- MessagePack (`zio-schema-msg-pack`)
- XML (`zio-schema-xml`)

The [Modules](index.md#modules) section lists the platforms each module supports, and [Codecs](derivations/codecs/index.md) describes each codec. ZIO Schema has no CSV, YAML, or query-string codec; a custom codec can add one.

Because the schema is an ordinary value, the same description of a type supports many tasks beyond serialization:

- Converting between formats, for example decoding JSON into a value and encoding that value as XML or Protobuf.
- Validating decoded values against the rules declared on their schema. Codecs do not run these rules for you.
- Computing the difference between two values and applying it as a patch, as [Diffing and Patching](operations/diffing-and-patching.md) shows.
- Migrating values from one version of a type to the next, for example the events of an event-sourced system, as [Schema Migration](operations/schema-migration.md) shows.
- Mapping a data transfer object to a domain type and back, as [How to Map a DTO to a Domain Object](examples/mapping-dto-to-domain-object.md) shows.

These tasks combine into transformation pipelines. For example, a service can decode a `PersonDTO` from Protobuf, transform it into a `Person`, validate it, encode it as JSON, and send the JSON text over the network.
