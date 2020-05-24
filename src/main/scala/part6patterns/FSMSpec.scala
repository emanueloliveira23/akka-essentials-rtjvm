package part6patterns

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}

import org.scalatest.{BeforeAndAfterAll, OneInstancePerTest, WordSpecLike}

class FSMSpec extends TestKit(ActorSystem("FSMSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with OneInstancePerTest {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import FSMSpec._
  import VendingMachine._

  "A vending machine" should {
    vendingMachineTestSuite(Props[VendingMachine])
  }

  "A vending machine FSM" should {
    vendingMachineTestSuite(Props[VendingMachineFSM])
  }

  def vendingMachineTestSuite(props: Props) = {

    "error when not initialized" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! RequestProduct("coke")
      expectMsg(VendingError(VendingErrors.MachineNotInitialize))
    }

    "report a product not available" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
      vendingMachine ! RequestProduct("sandwich")
      expectMsg(VendingError(VendingErrors.ProductNotAvailable))
    }

    "throw a timeout if a don't insert money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(Instructions.insertMoney(1)))
      within(1.5 seconds) {
        expectMsg(VendingError(VendingErrors.RequestTimedOut))
      }
    }

    "handle the reception of partial money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(Instructions.insertMoney(3)))
      vendingMachine ! ReceiveMoney(1)
      expectMsg(Instruction(Instructions.insertMoney(2)))
      within(1.5 seconds) {
        expectMsg(VendingError(VendingErrors.RequestTimedOut))
        expectMsg(GiveBackChange(1))
      }
    }

    "deliver the product if I insert all the money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(Instructions.insertMoney(3)))
      vendingMachine ! ReceiveMoney(3)
      expectMsg(Deliver("coke"))
    }

    "give back change and be able to request money for a new product" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(Instructions.insertMoney(3)))

      vendingMachine ! ReceiveMoney(4)
      expectMsg(Deliver("coke"))
      expectMsg(GiveBackChange(1))

      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(Instructions.insertMoney(3)))
    }
  }

}

object FSMSpec {

  /*
    Vending machine - VM
   */
  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])
  case class RequestProduct(product: String)

  case class Instruction(instruction: String) // message the VM will show on its "screen"
  case class ReceiveMoney(amount: Int)
  case class Deliver(product: String)
  case class GiveBackChange(amount: Int)

  case class VendingError(reason: String)
  case object ReceiveMoneyTimeout

  object VendingMachine {
    private val WaitMoneyTimeout = 1 second
    object VendingErrors {
      val MachineNotInitialize = "Machine not initialized"
      val ProductNotAvailable = "Product not available"
      val RequestTimedOut = "Request money timed out"
      val CommandNotFound = "Command not found"
    }
    object Instructions {
      def insertMoney(amount: Int) = s"Please insert $amount dollars"
    }
  }

  class VendingMachine extends Actor with ActorLogging {
    import VendingMachine._
    implicit val ec: ExecutionContext = context.dispatcher
    def receive: Receive = idle
    private def idle: Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender() ! VendingError(VendingErrors.MachineNotInitialize)
    }
    private def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) => inventory.get(product) match {
        case None | Some(0) =>
          sender() ! VendingError(VendingErrors.ProductNotAvailable)
        case Some(_) =>
          val price = prices(product)
          sender() ! Instruction(Instructions.insertMoney(price))
          context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule, sender()))
      }
    }
    private def waitForMoney(
      inventory: Map[String, Int],
      prices: Map[String, Int],
      product: String,
      money: Int,
      moneyTimeoutSchedule: Cancellable,
      requester: ActorRef
    ): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError(VendingErrors.RequestTimedOut)
        if (money > 0) requester ! GiveBackChange(money)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (money + amount >= price) {
          // user buys product
          requester ! Deliver(product)
          // deliver change
          val change = money + amount - price
          if (change > 0 ) requester ! GiveBackChange(change)
          // update inventory
          val nextStock = inventory(product) - 1
          val nextInventory = inventory + (product -> nextStock)
          context.become(operational(nextInventory, prices))
        } else {
          val remainingMoney = price - money - amount
          requester ! Instruction(Instructions.insertMoney(remainingMoney))
          context.become(waitForMoney(
            inventory, prices, product, // don't change
            money + amount, // user has inserted some money
            startReceiveMoneyTimeoutSchedule, // I need to set the timeout again
            requester
          ))
        }
    }
    private def startReceiveMoneyTimeoutSchedule = context.system.scheduler.scheduleOnce(WaitMoneyTimeout) {
      self ! ReceiveMoneyTimeout
    }
  }

  // step 1 - define the states and the data of the actor
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitForMoney extends VendingState

  trait VendingData
  case object Uninitialized extends VendingData
  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData
  case class WaitForMoneyData(
    inventory: Map[String, Int],
    prices: Map[String, Int],
    product: String,
    money: Int,
    requester: ActorRef
  ) extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] {
    import VendingMachine._

    // we don't have a receive handler
    // an EVENT(message, data)

    /*
      state, data
      event => state and data can be changed

      state = Idle
      data = Uninitialized

      event(Initialized(Map(coke -> 10), Map(coke -> 1))) =>
        state = Operational
        data = Initialized(inventory, prices)

      event(RequestProduct(coke)) =>
        state = WaitForMoney
        data = WaitForMoneyData(Map(coke -> 10), Map(coke -> 1), coke, 0, R)

      event(ReceiveMoney(2)) =>
        state = Operational
        data = Initialized(Map(coke -> 9), Map(coke -> 1))
     */

    startWith(Idle, Uninitialized)

    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        goto(Operational) using Initialized(inventory, prices)
        // equivalent with context.become(operational(inventory, prices))
      case _ =>
        sender() ! VendingError(VendingErrors.MachineNotInitialize)
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError(VendingErrors.ProductNotAvailable)
            stay()
          case Some(_) =>
            val price = prices(product)
            sender() ! Instruction(Instructions.insertMoney(price))
            goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, 0, sender())
        }
    }

    when(WaitForMoney, stateTimeout = 1 second) {
      case Event(StateTimeout, WaitForMoneyData(inventory, prices, _, money, requester)) =>
        requester ! VendingError(VendingErrors.RequestTimedOut)
        if (money > 0) requester ! GiveBackChange(money)
        goto(Operational) using Initialized(inventory, prices)

      case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requester)) =>
        val price = prices(product)
        if (money + amount >= price) {
          // user buys product
          requester ! Deliver(product)
          // deliver change
          val change = money + amount - price
          if (change > 0 ) requester ! GiveBackChange(change)
          // update inventory
          val nextStock = inventory(product) - 1
          val nextInventory = inventory + (product -> nextStock)
          goto(Operational) using Initialized(nextInventory, prices)
        } else {
          val remainingMoney = price - money - amount
          requester ! Instruction(Instructions.insertMoney(remainingMoney))
          stay() using WaitForMoneyData(inventory, prices, product, money + amount, requester)
        }
    }

    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError(VendingErrors.CommandNotFound)
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(s"Transitioning from $stateA to $stateB")
    }

    initialize()

  }

}
