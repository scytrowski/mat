package me.cytrowski.mat

object UnionBenchmark16:
  trait Unsupported01
  trait Unsupported02
  trait Unsupported03
  trait Unsupported04
  trait Unsupported05
  trait Unsupported06
  trait Unsupported07
  trait Unsupported08
  trait Unsupported09
  trait Unsupported10
  trait Unsupported11
  trait Unsupported12
  trait Unsupported13
  trait Unsupported14
  trait Unsupported15
  trait Unsupported16

  type LargeUnion =
    1
      | Unsupported01
      | Unsupported02
      | Unsupported03
      | Unsupported04
      | Unsupported05
      | Unsupported06
      | Unsupported07
      | Unsupported08
      | Unsupported09
      | Unsupported10
      | Unsupported11
      | Unsupported12
      | Unsupported13
      | Unsupported14
      | Unsupported15
      | Unsupported16

  val value = materializeOpt[LargeUnion]
