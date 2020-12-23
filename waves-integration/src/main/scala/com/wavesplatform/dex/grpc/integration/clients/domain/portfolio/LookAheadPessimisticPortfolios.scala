package com.wavesplatform.dex.grpc.integration.clients.domain.portfolio

import com.google.protobuf.ByteString
import com.wavesplatform.dex.domain.account.Address
import com.wavesplatform.dex.domain.asset.Asset

import scala.collection.mutable
import scala.util.chaining._

/**
 * Caches unknown forged transactions and don't add them in pending
 */
class LookAheadPessimisticPortfolios(orig: PessimisticPortfolios, maxForgedTransactions: Int) extends PessimisticPortfolios {

  private val forgedTxsEvictionQueue = new mutable.Queue[ByteString](maxForgedTransactions)
  private val forgedTxs = new mutable.HashSet[ByteString]

  override def getAggregated(address: Address): Map[Asset, Long] = orig.getAggregated(address)

  override def replaceWith(setTxs: Seq[PessimisticTransaction]): Set[Address] = {
    forgedTxs.clear()
    forgedTxsEvictionQueue.clear()
    orig.replaceWith(setTxs)
  }

  override def addPending(txs: Iterable[PessimisticTransaction]): Set[Address] =
    orig.addPending(txs.filterNot(remove)) // Without unknown

  /**
   * @return (affected addresses, unknown transactions)
   */
  override def processForged(txIds: Iterable[ByteString]): (Set[Address], List[ByteString]) =
    // We don't filter, because a transaction can't be forged twice
    orig.processForged(txIds).tap { case (_, unknownTxIds) =>
      unknownTxIds.foreach(put)
    }

  override def removeFailed(txIds: Iterable[ByteString]): Set[Address] =
    // txIds.foreach(remove) // a transaction can't be forged and failed both.
    orig.removeFailed(txIds)

  private def put(txId: ByteString): Unit = forgedTxs.add(txId).tap { added =>
    if (added) {
      if (forgedTxsEvictionQueue.size == maxForgedTransactions) forgedTxsEvictionQueue.removeLastOption().foreach(forgedTxs.remove)
      forgedTxsEvictionQueue.enqueue(txId)
    }
  }

  private def remove(tx: PessimisticTransaction): Boolean = remove(tx.txId)

  private def remove(id: ByteString): Boolean = forgedTxs.remove(id).tap { had =>
    if (had) forgedTxsEvictionQueue.removeFirst(_ == id)
  }

}