# mat

[![Scala](https://img.shields.io/badge/Scala-3.7.1-red.svg)](https://www.scala-lang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**`mat`** is a lightweight Scala 3 library for materializing types into values at compile time.

It provides a typeclass-based approach for turning types like tuples, literal types, or case classes into values using `inline` and `Mirror`.

---

## ✨ Features

- Materialize literal types like `5`, `"hello"`, `true`
- Recursively materialize tuples: `(1, "abc", true)`
- Materialize case classes via `Mirror.ProductOf`
- Materialize singleton sealed trait based ADTs via `Mirror.SumOf`
- Safe fallback with `materializeOpt[A]` returning `Option`

---

## 📦 Examples

### Materialize a literal value

```scala
import io.github.scytrowski.mat.*

val x: 42 = materialize[42]
// x: 42
```

### Materialize a tuple

```scala
import io.github.scytrowski.mat.*

val x: (true, 'd', "abc") = materialize[(true, 'd', "abc")]
// x: (true, 'd', "abc")
```

### Materialize a case object

```scala
import io.github.scytrowski.mat.*

case object SomeObject

val x: SomeObject.type = materialize[SomeObject.type]
// x: SomeObject
```

### Materialize a case class

```scala
import io.github.scytrowski.mat.*

case class SomeClass[A](a: A)

val x: SomeClass[15] = materialize[SomeClass[15]]
// x: SomeClass(15)
```

### Materialize a singleton ADT variant

```scala
import io.github.scytrowski.mat.*

sealed trait SomeADT

case object SingletonVariant extends SomeADT

val x: SingletonVariant.type = materialize[SomeADT]
// x: SingletonVariant
```

### Require a materializable type

```scala
import io.github.scytrowski.mat.*

def doSomethingWithMaterializableType[A: Materialize] = ???
```

---

## 🚧 TODO

- [ ] Support intersection types - e.g.: `5 & Int` should materialize as `5`
- [ ] Support union types - e.g.: `5 | String` should materialize as `5`
- [ ] Support nested singleton ADTs