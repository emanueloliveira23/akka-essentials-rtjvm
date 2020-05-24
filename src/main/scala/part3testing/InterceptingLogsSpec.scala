package part3testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class InterceptingLogsSpec extends TestKit(
  ActorSystem("InterceptingLogsSpec", ConfigFactory.load().getConfig("interceptingLogMessages")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import InterceptingLogsSpec._
  import CheckoutActor._

  val item = "Rock the JVM Akka course"
  val creditCard = "1234-1234-1234-1234"
  val invalidCreditCard = "0000-0000-000-0000"

  "A checkout flow" should {
    "correctly log the dispatcher of an order" in {
      EventFilter.info(pattern = s"Order [0-9]+ for item $item has been dispatched.", occurrences = 1) intercept {
        // our test code
        val checkout = system.actorOf(Props[CheckoutActor])
        checkout ! Checkout(item, creditCard)
      }
    }

    "freak out if the payment is denied" in {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkout = system.actorOf(Props[CheckoutActor])
        checkout ! Checkout(item, invalidCreditCard)
      }
    }
  }

}

object InterceptingLogsSpec {

  object CheckoutActor {
    case class Checkout(item: String, creditCard: String)
  }
  class CheckoutActor extends Actor {
    import CheckoutActor._
    import PaymentManager._
    import FulfillmentManager._
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])
    def receive: Receive = awaitingCheckout
    private def awaitingCheckout: Receive = {
      case Checkout(item, card) =>
        paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))
    }
    private def pendingPayment(item: String): Receive = {
      case PaymentAccepted =>
        fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfillment(item))
      case PaymentDenied =>
        throw new RuntimeException("I can't handle this anymore")
    }
    private def pendingFulfillment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }
  }

  object PaymentManager {
    case class AuthorizeCard(creditCard: String)
    case object PaymentAccepted
    case object PaymentDenied
  }
  class PaymentManager extends Actor {
    import PaymentManager._
    def receive: Receive = {
      case AuthorizeCard(card) =>
        if (card.startsWith("0")) sender() ! PaymentDenied
        else sender() ! PaymentAccepted
    }
  }

  object FulfillmentManager {
    case class DispatchOrder(item: String)
    case object OrderConfirmed
  }
  class FulfillmentManager extends Actor with ActorLogging {
    import FulfillmentManager._
    var orderId = 0
    def receive: Receive = {
      case DispatchOrder(item) =>
        orderId += 1
        log.info(s"Order $orderId for item $item has been dispatched.")
        sender() ! OrderConfirmed
    }
  }

}
