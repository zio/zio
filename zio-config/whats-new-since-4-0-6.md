# What's New Since 4.0.6

> This page lists the user-visible changes made after the 4.0.6 release, grouped by release. Read it when you upgrade from 4.0.6 or earlier to a later 4.x version.

## 4.1.0

Version 4.1.0 fixes the cats module under Scala 3.9 and moves the Scala 3 build to the 3.9.0 compiler. It also updates the typesafe config library to 1.4.9 and snakeyaml-engine to 3.1.1. The project's CI no longer runs on JDK 11 and now runs on JDK 25.

## 4.0.8

Version 4.0.8 adds the `zio-config-toml` module, which reads TOML files and strings. The module provides `ConfigProvider.fromTomlString`, `fromTomlFile`, and `fromTomlPath`, plus `ZIO` variants of each. See [Read from various Sources](./read-from-various-sources.md) for examples.

## 4.0.7

Version 4.0.7 adds the `@kebabCaseLegacy` annotation for derived configs. It applies the older kebab-case rules, which split a key only at a lowercase-to-uppercase boundary. Under these rules `myValue123` becomes `my-value123`, and `QAndA` becomes `qand-a`. Use it to keep the key names of configs written before the default kebab-case rules changed.

## After 4.1.0

The `series/4.x` branch has dependency updates after the 4.1.0 release, including typesafe config 1.4.10 and snakeyaml-engine 3.2. These updates change the libraries that zio-config uses internally. They do not change the zio-config API.
