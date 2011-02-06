package org.samples.trading.actor

import org.junit._
import Assert._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.actors._
import scala.actors.threadpool._

import org.samples.trading.domain._
import org.samples.trading.common._

class ActorPerformanceTest extends BenchmarkScenarios // with OtherPerformanceScenarios 
{
  type TS = ActorTradingSystem
  
  val threadPool: ExecutorService = Executors.newFixedThreadPool(maxClients)

  override def createTradingSystem: TS = new ActorTradingSystem

  override def placeOrder(orderReceiver: ActorOrderReceiver, order: Order): Rsp = {
    val r = orderReceiver.asInstanceOf[ActorOrderReceiver] !? order
    val rsp = r match {
      case r: Rsp => r
      case _ => new Rsp(false)
    }
    rsp
  }


  // need this so that junit will detect this as a test case
  @Test
  def dummy {}

  override def runScenario(scenario: String, orders: List[Order], repeat: Int, numberOfClients: Int, delayMs: Int) = {
    val totalNumberOfRequests = orders.size * repeat
    val repeatsPerClient = repeat / numberOfClients
    val oddRepeats = repeat - (repeatsPerClient * numberOfClients)
    val latch = new CountDownLatch(numberOfClients)
    val receivers = tradingSystem.orderReceivers.toIndexedSeq
    val clients = (for (i <- 0 until numberOfClients) yield {
      val receiver = receivers(i % receivers.size)
      new Client(receiver, orders, latch, repeatsPerClient + (if (i < oddRepeats) 1 else 0), delayMs)
    }).toList

    val start = System.nanoTime
    clients.foreach(_.start)
    val ok = latch.await(5000 + (2 + delayMs) * totalNumberOfRequests, TimeUnit.MILLISECONDS)
    val durationNs = (System.nanoTime - start)

    assertTrue(ok)
    assertEquals((orders.size / 2) * repeat, TotalTradeCounter.counter.get)
    logMeasurement(scenario, numberOfClients, durationNs)
  }

  class Client(orderReceiver: ActorOrderReceiver, orders: List[Order], latch: CountDownLatch, repeat: Int, delayMs: Int) extends Actor {

    override def scheduler = new SchedulerAdapter {
      def execute(block: => Unit) =
        threadPool.execute(new Runnable {
          def run() { block }
        })
    }
    
    def this(orderReceiver: ActorOrderReceiver, orders: List[Order], latch: CountDownLatch, repeat: Int) {
      this (orderReceiver, orders, latch, repeat, 0)
    }

    def act() {
      (1 to repeat).foreach(i =>
        {
          //					println("Client repeat: " + i)
          for (o <- orders) {
            val t0 = System.nanoTime
            val rsp = placeOrder(orderReceiver, o)
            val duration = System.nanoTime - t0
            stat.addValue(duration)
            if (!rsp.status) {
              println("Invalid rsp")
            }
            delay(delayMs)
          }
        }
        )
      latch.countDown()
      exit
    }
  }

}


