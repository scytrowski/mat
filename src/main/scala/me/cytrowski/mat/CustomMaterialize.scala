package me.cytrowski.mat

/** Type class allowing to extend or override the default materialization logic
  * for values of type `A`.
  *
  * This trait can be implemented to provide custom materialization rules for
  * types that are not handled by the built-in macro rules.
  *
  * The intended use case is for library authors or advanced users who want to
  * enable materialization of types that require special treatment or fall
  * outside the standard cases (e.g. constant types, tuples, products, singleton
  * sums).
  *
  * An explicit `Materialize[A]` instance is checked first. When one is not
  * available, a `CustomMaterialize[A]` instance takes precedence over the
  * built-in macro rules and can override their behavior.
  *
  * @tparam A
  *   the type to materialize
  */
trait CustomMaterialize[A]:
  /** The resulting type of the materialized value. */
  type Out <: A

  /** Materializes a value of type `Out`, satisfying the structure of `A`. */
  def apply(): Out

object CustomMaterialize:
  /** Type alias that fixes the precise output type of custom evidence. */
  type Aux[A, O <: A] = CustomMaterialize[A] { type Out = O }
