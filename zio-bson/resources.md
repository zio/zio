# Resources

> This page collects the places to find the source, the API documentation and help for ZIO BSON.

## Source and API

The code is in the [zio/zio-bson](https://github.com/zio/zio-bson) repository on GitHub, where issues and pull requests are welcome. The Scaladoc of every release is on [javadoc.io](https://javadoc.io/doc/dev.zio/zio-bson_2.13), and the artifacts are on [Maven Central](https://central.sonatype.com/artifact/dev.zio/zio-bson_2.13). Release notes are on the [releases page](https://github.com/zio/zio-bson/releases).

## Related Projects

- [MongoDB Java driver](https://www.mongodb.com/docs/drivers/java/sync/current/) provides `BsonValue`, `BsonReader`, `BsonWriter` and the `CodecProvider` that ZIO BSON plugs into.
- [zio-schema](https://github.com/zio/zio-schema/) provides the `zio-schema-bson` module, which derives BSON codecs from a `Schema`.
- [Magnolia](https://github.com/softwaremill/magnolia) is the derivation library that `zio-bson-magnolia` is built on.

## Community

Questions about ZIO BSON are welcome on the [ZIO Discord](https://discord.gg/2ccFBr4) in the ecosystem channels.
