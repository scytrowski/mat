package io.github.scytrowski.mat

import scala.deriving.Mirror

sealed trait SingletonSum[A]:
  type Repr <: A

object SingletonSum:
  type Aux[A, R <: A] = SingletonSum[A] { type Repr = R }

  given [A, S <: A] => SumOfElemTypesAux[A, S *: EmptyTuple] => SingletonSum[A]:
    override type Repr = S

  private type SumOfElemTypesAux[A, T <: Tuple] = Mirror.SumOf[A] { type MirroredElemTypes = T }
