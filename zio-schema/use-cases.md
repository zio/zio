# ZIO Schema Use cases

> ZIO Schema allows us to create representations of our data types as values.

ZIO Schema allows us to create representations of our data types as values.

Once we have a representation of our data types, we can use it to
  - Serialize and deserialize our types
  - Validate our types
  - Transform our types
  - Create instances of your types

We can then use one of the various codecs (or create our own) to serialize and deserialize your types.

Example of possible codecs are:

  - CSV Codec
  - JSON Codec (already available)
  - Apache Avro Codec (in progress)
  - Apache Thrift Codec (in progress)
  - XML Codec
  - YAML Codec
  - Protobuf Codec (already available)
  - QueryString Codec
  - etc.

Example use cases that are possible:

  - Serializing and deserializing JSON
  - Serializing and deserializing XML
  - Validating JSON
  - Validating XML
  - Transforming JSON
  - Transforming XML
  - Transforming JSON to XML
  - Transforming XML to JSON
  - Creating diffs from arbitrary data structures
  - Creating migrations / evolutions e.g. of Events used in Event-Sourcing
  - Transformation pipelines, e.g.
      1. Convert from protobuf to object, e.g. `PersonDTO`,
      2. Transform to another representation, e.g. `Person`,
      3. Validate
      4. Transform to JSON `JsonObject`
      5. Serialize to `String`
