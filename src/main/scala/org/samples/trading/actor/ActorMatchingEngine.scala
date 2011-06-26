package org.samples.trading.actor

import scala.actors._
import org.samples.trading.common.MatchingEngine
import org.samples.trading.domain.Order
import org.samples.trading.domain.Orderbook
import org.samples.trading.domain.Rsp
import org.samples.trading.domain.SupportedOrderbooksReq

class ActorMatchingEngine(val meId: String, val orderbooks: List[Orderbook])
  extends Actor with MatchingEngine {
  var standby: Option[ActorMatchingEngine] = None

  //  override def scheduler = new SchedulerAdapter {
  //      def execute(block: => Unit) =
  //        threadPool.execute(new Runnable {
  //          def run() { block }
  //        })
  //    }

  def act() {
    loop {
      react {
        case SupportedOrderbooksReq => 
          reply(orderbooks)
        case order: Order =>
          handleOrder(order)
        case "exit" =>
          standby.foreach(_ !! "exit")
          txLog.close()
          exit
        case unknown => 
          println("Received unknown message: " + unknown)
      }
    }
  }

  def handleOrder(order: Order) {
    orderbooksMap.get(order.orderbookSymbol) match {
      case Some(orderbook) =>
        // println(meId + " " + order)

        val pendingStandbyReply: Option[Future[Any]] = 
          for (s <- standby) yield { s !! order }
        
        txLog.storeTx(order)
        orderbook.addOrder(order)
        orderbook.matchOrders()
        // wait for standby reply
        pendingStandbyReply.foreach(waitForStandby(_))
        reply(new Rsp(true))
      case None =>
        println("Orderbook not handled by this MatchingEngine: " + order.orderbookSymbol)
        reply(new Rsp(false))
    }
  }

  def waitForStandby(pendingStandbyFuture: Future[_]) {
    // apply will wait
    pendingStandbyFuture()
    if (!pendingStandbyFuture.isSet) {
      println("### standby timeout")
    }
  }

}
