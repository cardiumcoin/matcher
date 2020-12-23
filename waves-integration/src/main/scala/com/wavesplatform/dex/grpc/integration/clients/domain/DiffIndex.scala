package com.wavesplatform.dex.grpc.integration.clients.domain

import cats.Monoid
import cats.instances.map._
import cats.instances.set._
import cats.syntax.semigroup._
import com.wavesplatform.dex.domain.account.Address
import com.wavesplatform.dex.domain.asset.Asset

case class DiffIndex(regular: Map[Address, Set[Asset]], outLeases: Set[Address]) {

  // TODO DEX-1003, also probably create a common implementation for intersect too
  def without(that: DiffIndex): DiffIndex = DiffIndex(
    regular = regular.view
      .map { case (address, assets) => address -> that.regular.get(address).fold(assets)(assets.--) }
      .filterNot(_._2.isEmpty)
      .toMap,
    outLeases = outLeases -- that.outLeases
  )

  def intersect(that: DiffIndex): DiffIndex = DiffIndex(
    regular = regular.view
      .map { case (address, assets) => address -> that.regular.get(address).fold(Set.empty[Asset])(assets.intersect) }
      .filterNot(_._2.isEmpty)
      .toMap,
    outLeases = outLeases.intersect(that.outLeases)
  )

  def isEmpty: Boolean = regular.isEmpty && outLeases.isEmpty

}

object DiffIndex {

  // TODO DEX-1002
  implicit val diffIndexMonoid: Monoid[DiffIndex] = new Monoid[DiffIndex] {
    override val empty = DiffIndex(Map.empty, Set.empty)

    override def combine(x: DiffIndex, y: DiffIndex): DiffIndex = DiffIndex(
      regular = x.regular |+| y.regular,
      outLeases = x.outLeases |+| y.outLeases
    )

  }

}