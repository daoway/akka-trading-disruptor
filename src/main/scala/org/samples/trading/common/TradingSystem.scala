package org.samples.trading.common

import org.samples.trading.domain.Orderbook
import org.samples.trading.domain.OrderbookRepository
import org.samples.trading.domain.SimpleTradeObserver


trait TradingSystem {
  type ME
  type OR

  val allOrderbookSymbols: List[String] = OrderbookRepository.allOrderbookSymbols

  val orderbooksGroupedByMatchingEngine: List[List[Orderbook]] =
    for (groupOfSymbols: List[String] <- OrderbookRepository.orderbookSymbolsGroupedByMatchingEngine)
    yield groupOfSymbols map (s => new Orderbook(s) with SimpleTradeObserver)

  def useStandByEngines: Boolean = true
  // pairs of primary-standby matching engines
  lazy val matchingEngines: Map[ME, Option[ME]] = createMatchingEngines

  def createMatchingEngines: Map[ME, Option[ME]]

  lazy val orderReceivers: List[OR] = createOrderReceivers

  def createOrderReceivers: List[OR]

  def start()

  def shutdown()

}