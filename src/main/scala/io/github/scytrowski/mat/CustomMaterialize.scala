package io.github.scytrowski.mat

/** Type class allowing to extend or override the default materialization logic
  * for values of type `A`.
  *
  * This trait can be implemented to provide custom materialization rules for
  * types that are not handled by the default `Materialize` implementation.
  *
  * The intended use case is for library authors or advanced users who want to
  * enable materialization of types that require special treatment or fall
  * outside the standard cases (e.g. constant types, tuples, products, singleton
  * sums).
  *
  * When a `CustomMaterialize[A]` instance is in scope, it will be used as a
  * fallback during materialization if no built-in instance of `Materialize[A]`
  * is available.
  */
trait CustomMaterialize[A]:
  /** The resulting type of the materialized value. */
  type Out <: A

  /** Materializes a value of type `Out`, satisfying the structure of `A`. */
  def apply(): Out

object CustomMaterialize:
  type Aux[A, O <: A] = CustomMaterialize[A] { type Out = O }
