package me.cytrowski.mat

object TupleBenchmark16:
  type LargeTuple = (
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13,
      14,
      15,
      16
  )

  val value: LargeTuple = materialize[LargeTuple]
