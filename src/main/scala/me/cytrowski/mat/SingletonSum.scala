package me.cytrowski.mat

import scala.deriving.Mirror

/** Compile-time evidence for a sum whose mirrored representation has one case.
  *
  * This is an implementation-level helper used by the materialization macro.
  * Application code normally does not need to construct or summon it directly.
  *
  * @tparam A
  *   the sum type
  */
sealed trait SingletonSum[A]:
  /** The single represented case of `A`. */
  type Repr <: A

/** Type-level helpers for [[SingletonSum]]. */
object SingletonSum:
  /** Type alias that fixes the represented case of a singleton sum. */
  type Aux[A, R <: A] = SingletonSum[A] { type Repr = R }

  /** Provides evidence when the sum mirror contains exactly one element type
    * `S`.
    */
  given [A, S <: A] => SumOfElemTypesAux[A, S *: EmptyTuple] => SingletonSum[A]:
    override type Repr = S

  private type SumOfElemTypesAux[A, T <: Tuple] = Mirror.SumOf[A] {
    type MirroredElemTypes = T
  }
