package me.cytrowski.mat

object ProductBenchmark24:
  case class LargeProduct(
      f01: 1,
      f02: 2,
      f03: 3,
      f04: 4,
      f05: 5,
      f06: 6,
      f07: 7,
      f08: 8,
      f09: 9,
      f10: 10,
      f11: 11,
      f12: 12,
      f13: 13,
      f14: 14,
      f15: 15,
      f16: 16,
      f17: 17,
      f18: 18,
      f19: 19,
      f20: 20,
      f21: 21,
      f22: 22,
      f23: 23,
      f24: 24
  )

  val value: LargeProduct = materialize[LargeProduct]
