# Changelog

All notable changes to `mat` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-09-15

### Changed

- Unified custom materialization around explicit `Materialize[A]` evidence.
- `Materialize.fromValue` is now used during recursive derivation of products,
  tuples, intersections and sums.
- Explicit `Materialize[A]` evidence takes precedence over built-in derivation
  at every level of a materialized type.
- Precise output types continue to be available through
  `Materialize.Aux[A, O]`.

### Removed

- Removed the redundant `CustomMaterialize[A]` API.

### Migration

Replace custom evidence with `Materialize.fromValue`:

```scala
given Materialize.Aux[Int, 5] =
  Materialize.fromValue[Int, 5](5)
```

For values whose output type is the same as the requested type, the shorter
form remains available:

```scala
given Materialize[Configuration] =
  Materialize.fromValue(Configuration("default"))
```

[Keep a Changelog]: https://keepachangelog.com/en/1.1.0/
[Semantic Versioning]: https://semver.org/
