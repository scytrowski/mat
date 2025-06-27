package com.github.scytrowski.mat

import scala.compiletime.{summonFrom, error}

inline def materialize[A]: A =
  inline materializeOpt[A] match
    case Some(a) => a
    case None => error("Type cannot be materialized")

inline def materializeOpt[A]: Option[A] =
  summonFrom {
    case mat: Materialize[A] => Some(mat())
    case _ => None
  }

sealed trait Materialize[A]:
  def apply(): A

object Materialize:
  def apply[A](using mat: Materialize[A]): Materialize[A] = mat

given materializeConstValue: [A : ValueOf] => Materialize[A]:
  override def apply(): A = valueOf[A]

given materializeEmptyTuple: Materialize[EmptyTuple]:
  override def apply(): EmptyTuple = EmptyTuple

given materializeTuple: [H : Materialize, T <: Tuple : Materialize] => Materialize[H *: T]:
  override def apply(): H *: T = Materialize[H]() *: Materialize[T]()
