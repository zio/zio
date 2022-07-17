---
id: faq
title: "Frequently Answered Questions (FAQ)"
sidebar_label: "FAQ"
---

In this page we are going to answer general questions related to the ZIO project.

## In ZIO ecosystem, there are lots of data types which they have `Z` prefix in their names. What this prefix stands for? Does it mean, that data type is effectual?

No, it doesn't denote that the data type is effectual. Instead, the `Z` prefix is used for two purposes:

1. **Polymorphic Version of Another Data Type** — The `Z` prefix indicates a more polymorphic version of another data type, not a data type that is effectual. So for example `IO` and `ZIO` are equally effectual but `ZIO` is more polymorphic because it has the additional type parameter `R`.

2. **Term Disambiguation** — There are some cases where the `Z` prefix is used to disambiguate a term that might otherwise be too common and create risk of name conflicts (e.g. `ZPool`).

This convention is true across all ZIO ecosystem. For example, in ZIO Prelude, the `ZValidation` is a more general version of `Validation` that is polymorphic in the log type. `ZSet` is a more polymorphic version of a _Set_ that is polymorphic in the measure type. `ZPure` is more polymorphic than its type aliases in several ways as represented by its different type parameters and also serves to disambiguate it as _Pure_ which is too general.
