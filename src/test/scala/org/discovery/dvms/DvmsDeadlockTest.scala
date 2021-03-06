package org.discovery.dvms


import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import log.LoggingActor
import org.discovery.dvms.dvms._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.dvms.DvmsModel.DvmsPartititionState._
import org.discovery.dvms.dvms.DvmsProtocol._
import entropy.{AbstractEntropyActor, FakeEntropyActor}
import factory.DvmsAbstractFactory
import monitor.{AbstractMonitorActor, FakeMonitorActor}
import org.scalatest.{BeforeAndAfterEach, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import akka.pattern.ask
import collection.immutable.HashMap
import org.discovery.peeractor.util.{NodeRef, INetworkLocation}
import org.discovery.peeractor.util.Configuration
import org.discovery.peeractor.PeerActorProtocol.ConnectToThisPeerActor
import scala.Some
import org.discovery.peeractor.util.FakeNetworkLocation
import java.util.Date
import com.typesafe.config.ConfigFactory
import org.discovery.peeractor.overlay.chord.ChordService
import service.ServiceActor
import org.discovery.DiscoveryModel.model.ReconfigurationModel._
import org.discovery.peeractor.overlay.OverlayService
import org.discovery.dvms.utility.FakePlanApplicator


object DvmsDeadlockTest {

}


object TestData {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  val hashLoad: HashMap[INetworkLocation, List[Double]] = HashMap(
    (intToLocation(1) ->  List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(2) ->  List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(3) ->  List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(4) ->  List(110, 20, -1, -1, -1, -1, -1)),
    (intToLocation(5) ->  List(110, -1, 30, -1, -1, -1, -1)),
    (intToLocation(6) ->  List(110, 20, -1, -1, -1, -1, -1)),
    (intToLocation(7) ->  List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(8) ->  List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(9) ->  List(110, 20, -1, -1, -1, -1, -1)),
    (intToLocation(10) -> List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(11) -> List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(12) -> List(110, 20, -1, -1, -1, -1, -1))
  )
}


class TestMonitorActor(nodeRef: NodeRef) extends FakeMonitorActor(nodeRef) {

  var count: Int = -1

  override def uploadCpuConsumption(): Double = {

    count = count + 1

    if (TestData.hashLoad(nodeRef.location).size > count) {

      TestData.hashLoad(nodeRef.location)(count) match {
        case -1 =>
        case n: Double => {
          cpuConsumption = n
        }
      }
    }

    cpuConsumption
  }
}

trait TestDvmsMessage extends DvmsMessage

case class ReportIn() extends TestDvmsMessage

case class SetCurrentPartition(partition: DvmsPartition) extends TestDvmsMessage

//case class SetFirstOut(firstOut: NodeRef) extends TestDvmsMessage

case class BeginTransmission() extends TestDvmsMessage

object TestDvmsActor {
  var experimentHasStarted: Boolean = false
}

class TestDvmsActor(applicationRef: NodeRef, overlayService: OverlayService) extends LocalityBasedScheduler(applicationRef, overlayService, new FakePlanApplicator()) {



  override def receive = {

//    case msg@SetCurrentPartition(partition) => {
//      currentPartition = Some(partition)
//      lastPartitionUpdateDate = Some(new Date())
//    }
//
//
//    case msg@CpuViolationDetected() => {
//      if(TestDvmsActor.experimentHasStarted) {
//        super.receive(msg)
//      }
//    }
//
////    case msg@SetFirstOut(node) => {
////      firstOut = Some(node)
////    }
//
//    case BeginTransmission() => {
//      currentPartition match {
//        case Some(p) =>
//          firstOut match {
//            case Some(actor) =>
//              actor.ref ! TransmissionOfAnISP(p)
//            case None =>
//          }
//          case None =>
//      }
//    }

    case ReportIn() =>
      if(currentPartition != None) {
        sender ! false
      } else {
        sender ! true
      }

    case msg => super.receive(msg)
  }
}

object TestEntropyActor {
  var failureCount: Int = 0
  var successCount: Int = 0
}

class TestEntropyActor(nodeRef: NodeRef) extends FakeEntropyActor(nodeRef) {

  override def computeReconfigurationPlan(nodes: List[NodeRef]): ReconfigurationResult = {

    val result = super.computeReconfigurationPlan(nodes)

    result match {
      case solution: ReconfigurationSolution => {
        TestEntropyActor.successCount += 1
      }
      case ReconfigurationlNoSolution() => {
        TestEntropyActor.failureCount += 1
      }
    }

    result
  }

  override def receive = {
    case ReportIn() => sender !(TestEntropyActor.failureCount, TestEntropyActor.successCount)
    case msg => {
      super.receive(msg)
    }
  }
}

class TestLogginActor(location: INetworkLocation) extends LoggingActor(location) {

  override def receive = {
    case _ =>
  }
}


object TestDvmsFactory extends DvmsAbstractFactory {
  def createMonitorActor(nodeRef: NodeRef): Option[AbstractMonitorActor] = {
    Some(new TestMonitorActor(nodeRef))
  }

  def createDvmsActor(nodeRef: NodeRef, overlayService: OverlayService): Option[SchedulerActor] = {
    Some(new TestDvmsActor(nodeRef, overlayService))
  }

  def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor] = {
    Some(new TestEntropyActor(nodeRef))
  }

  def createLoggingActor(nodeRef: NodeRef): Option[LoggingActor] = {
    Some(new TestLogginActor(nodeRef.location))
  }

  def createServiceActor(nodeRef: NodeRef, overlayService: ChordService): Option[ServiceActor] = {
    Some(new ServiceActor(nodeRef, overlayService))
  }
}


class DvmsDeadlockTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit val timeout = akka.util.Timeout(1 seconds)

  Configuration.debug = true

  def this() = this(ActorSystem("MySpec", ConfigFactory.parseString( """
     prio-dispatcher {
       mailbox-type = "org.discovery.dvms.utility.DvmsPriorityMailBox"
     }
                                                                     """)))

  override def beforeEach() {
    Thread.sleep(1000)
  }

  override def afterAll() {
    system.shutdown()
  }

  "Deadlock resolver" must {


    "resolve a linear deadlock" in {

      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

      def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

      val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))
      val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)))
      val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)))
      val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)))
      val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)))
      val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)))
      val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)))
      val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)))


      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)
      node5 ! ConnectToThisPeerActor(node1)
      node6 ! ConnectToThisPeerActor(node1)
      node7 ! ConnectToThisPeerActor(node1)
      node8 ! ConnectToThisPeerActor(node1)


      Thread.sleep(500)


      val node1Ref = quickNodeRef(1, node1)
      val node2Ref = quickNodeRef(2, node2)
      val node3Ref = quickNodeRef(3, node3)
      val node4Ref = quickNodeRef(4, node4)
      val node5Ref = quickNodeRef(5, node5)
      val node6Ref = quickNodeRef(6, node6)
      val node7Ref = quickNodeRef(7, node7)
      val node8Ref = quickNodeRef(8, node8)

      // init the partitions
      val partition_1_2 = DvmsPartition(node2Ref, node1Ref, List(node1Ref, node2Ref), Growing())
      val partition_3_4 = DvmsPartition(node4Ref, node3Ref, List(node3Ref, node4Ref), Growing())
      val partition_5_6 = DvmsPartition(node6Ref, node5Ref, List(node5Ref, node6Ref), Growing())
      val partition_7_8 = DvmsPartition(node8Ref, node7Ref, List(node7Ref, node8Ref), Growing())

      node1 ! SetCurrentPartition(partition_1_2)
      node2 ! SetCurrentPartition(partition_1_2)

      node3 ! SetCurrentPartition(partition_3_4)
      node4 ! SetCurrentPartition(partition_3_4)

      node5 ! SetCurrentPartition(partition_5_6)
      node6 ! SetCurrentPartition(partition_5_6)

      node7 ! SetCurrentPartition(partition_7_8)
      node8 ! SetCurrentPartition(partition_7_8)

//      node1 ! SetFirstOut(node3Ref)
//      node2 ! SetFirstOut(node3Ref)
//
//      node3 ! SetFirstOut(node5Ref)
//      node4 ! SetFirstOut(node5Ref)
//
//      node5 ! SetFirstOut(node7Ref)
//      node6 ! SetFirstOut(node7Ref)
//
//      node7 ! SetFirstOut(node1Ref)
//      node8 ! SetFirstOut(node1Ref)

      // transmission of ISP to the respectives firstOuts
      TestDvmsActor.experimentHasStarted = true
      node2 ! BeginTransmission()
      node4 ! BeginTransmission()
      node6 ! BeginTransmission()
      node8 ! BeginTransmission()

      Thread.sleep(5000)

      val node1IsOk = Await.result(node1 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node2IsOk = Await.result(node2 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node3IsOk = Await.result(node3 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node4IsOk = Await.result(node4 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node5IsOk = Await.result(node5 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node6IsOk = Await.result(node6 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node7IsOk = Await.result(node7 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node8IsOk = Await.result(node8 ? ReportIn(), 1 second).asInstanceOf[Boolean]

      println(s"1: $node1IsOk")
      println(s"2: $node2IsOk")
      println(s"3: $node3IsOk")
      println(s"4: $node4IsOk")
      println(s"5: $node5IsOk")
      println(s"6: $node6IsOk")
      println(s"7: $node7IsOk")
      println(s"8: $node8IsOk")

      (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk &&
        node7IsOk && node8IsOk) must be(true)

      node1 ! Kill
      node2 ! Kill
      node3 ! Kill
      node4 ! Kill
      node5 ! Kill
      node6 ! Kill
      node7 ! Kill
      node8 ! Kill

      system.shutdown()
      TestDvmsActor.experimentHasStarted = false
    }


    "resolve a nested deadlock (4 nodes)" in {

      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

      def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

      val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))
      val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)))
      val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)))
      val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)))


      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)


      Thread.sleep(500)


      val node1Ref = quickNodeRef(1, node1)
      val node2Ref = quickNodeRef(2, node2)
      val node3Ref = quickNodeRef(3, node3)
      val node4Ref = quickNodeRef(4, node4)

      // init the partitions
      val partition_1_3 = DvmsPartition(node3Ref, node1Ref, List(node1Ref, node3Ref), Growing())
      val partition_2_4 = DvmsPartition(node4Ref, node2Ref, List(node2Ref, node4Ref), Growing())


      node1 ! SetCurrentPartition(partition_1_3)
      node3 ! SetCurrentPartition(partition_1_3)

      node2 ! SetCurrentPartition(partition_2_4)
      node4 ! SetCurrentPartition(partition_2_4)

//      node1 ! SetFirstOut(node2Ref)
//      node3 ! SetFirstOut(node4Ref)
//
//      node2 ! SetFirstOut(node3Ref)
//      node4 ! SetFirstOut(node1Ref)

      // transmission of ISP to the respectives firstOuts
      TestDvmsActor.experimentHasStarted = true
      node3 ! BeginTransmission()
      node4 ! BeginTransmission()

      Thread.sleep(5000)

      val node1IsOk = Await.result(node1 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node2IsOk = Await.result(node2 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node3IsOk = Await.result(node3 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node4IsOk = Await.result(node4 ? ReportIn(), 1 second).asInstanceOf[Boolean]

      println(s"1: $node1IsOk")
      println(s"2: $node2IsOk")
      println(s"3: $node3IsOk")
      println(s"4: $node4IsOk")

      (node1IsOk && node2IsOk && node3IsOk && node4IsOk) must be(true)

      node1 ! Kill
      node2 ! Kill
      node3 ! Kill
      node4 ! Kill

      system.shutdown()
      TestDvmsActor.experimentHasStarted = false
    }


    "resolve a nested deadlock (6 nodes)" in {

      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

      def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

      val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))
      val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)))
      val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)))
      val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)))
      val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)))
      val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)))


      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)
      node5 ! ConnectToThisPeerActor(node1)
      node6 ! ConnectToThisPeerActor(node1)


      Thread.sleep(500)


      val node1Ref = quickNodeRef(1, node1)
      val node2Ref = quickNodeRef(2, node2)
      val node3Ref = quickNodeRef(3, node3)
      val node4Ref = quickNodeRef(4, node4)
      val node5Ref = quickNodeRef(5, node5)
      val node6Ref = quickNodeRef(6, node6)

      // init the partitions
      val partition_1_3_5 = DvmsPartition(node5Ref, node1Ref, List(node1Ref, node3Ref, node5Ref), Growing())
      val partition_2_4_6 = DvmsPartition(node6Ref, node2Ref, List(node2Ref, node4Ref, node6Ref), Growing())


      node1 ! SetCurrentPartition(partition_1_3_5)
      node3 ! SetCurrentPartition(partition_1_3_5)
      node5 ! SetCurrentPartition(partition_1_3_5)

      node2 ! SetCurrentPartition(partition_2_4_6)
      node4 ! SetCurrentPartition(partition_2_4_6)
      node6 ! SetCurrentPartition(partition_2_4_6)

//      node1 ! SetFirstOut(node2Ref)
//      node3 ! SetFirstOut(node4Ref)
//      node5 ! SetFirstOut(node6Ref)
//
//      node2 ! SetFirstOut(node3Ref)
//      node4 ! SetFirstOut(node5Ref)
//      node6 ! SetFirstOut(node1Ref)

      // transmission of ISP to the respectives firstOuts
      TestDvmsActor.experimentHasStarted = true
      node5 ! BeginTransmission()
      node6 ! BeginTransmission()

      Thread.sleep(5000)

      val node1IsOk = Await.result(node1 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node2IsOk = Await.result(node2 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node3IsOk = Await.result(node3 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node4IsOk = Await.result(node4 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node5IsOk = Await.result(node5 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node6IsOk = Await.result(node6 ? ReportIn(), 1 second).asInstanceOf[Boolean]

      println(s"1: $node1IsOk")
      println(s"2: $node2IsOk")
      println(s"3: $node3IsOk")
      println(s"4: $node4IsOk")
      println(s"5: $node5IsOk")
      println(s"6: $node6IsOk")

      (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk) must be(true)

      node1 ! Kill
      node2 ! Kill
      node3 ! Kill
      node4 ! Kill
      node5 ! Kill
      node6 ! Kill

      system.shutdown()
      TestDvmsActor.experimentHasStarted = false
    }

    "resolve a nested deadlock (9 nodes)" in {

      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

      def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

      val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))
      val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)))
      val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)))
      val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)))
      val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)))
      val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)))
      val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)))
      val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)))
      val node9 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(9), TestDvmsFactory)))


      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)
      node5 ! ConnectToThisPeerActor(node1)
      node6 ! ConnectToThisPeerActor(node1)
      node7 ! ConnectToThisPeerActor(node1)
      node8 ! ConnectToThisPeerActor(node1)
      node9 ! ConnectToThisPeerActor(node1)


      Thread.sleep(500)


      val node1Ref = quickNodeRef(1, node1)
      val node2Ref = quickNodeRef(2, node2)
      val node3Ref = quickNodeRef(3, node3)
      val node4Ref = quickNodeRef(4, node4)
      val node5Ref = quickNodeRef(5, node5)
      val node6Ref = quickNodeRef(6, node6)
      val node7Ref = quickNodeRef(7, node7)
      val node8Ref = quickNodeRef(8, node8)
      val node9Ref = quickNodeRef(9, node9)

      // init the partitions
      val partition_1_3_5 = DvmsPartition(node5Ref, node1Ref, List(node1Ref, node3Ref, node5Ref), Growing())
      val partition_2_6_8 = DvmsPartition(node8Ref, node2Ref, List(node2Ref, node6Ref, node8Ref), Growing())
      val partition_4_7_9 = DvmsPartition(node9Ref, node4Ref, List(node4Ref, node7Ref, node9Ref), Growing())


      node1 ! SetCurrentPartition(partition_1_3_5)
      node3 ! SetCurrentPartition(partition_1_3_5)
      node5 ! SetCurrentPartition(partition_1_3_5)

      node2 ! SetCurrentPartition(partition_2_6_8)
      node6 ! SetCurrentPartition(partition_2_6_8)
      node8 ! SetCurrentPartition(partition_2_6_8)

      node4 ! SetCurrentPartition(partition_4_7_9)
      node7 ! SetCurrentPartition(partition_4_7_9)
      node9 ! SetCurrentPartition(partition_4_7_9)

//      node1 ! SetFirstOut(node2Ref)
//      node3 ! SetFirstOut(node4Ref)
//      node5 ! SetFirstOut(node6Ref)
//
//      node2 ! SetFirstOut(node3Ref)
//      node6 ! SetFirstOut(node7Ref)
//      node8 ! SetFirstOut(node9Ref)
//
//      node4 ! SetFirstOut(node5Ref)
//      node7 ! SetFirstOut(node8Ref)
//      node9 ! SetFirstOut(node1Ref)

      // transmission of ISP to the respectives firstOuts
      TestDvmsActor.experimentHasStarted = true
      node5 ! BeginTransmission()
      node8 ! BeginTransmission()
      node9 ! BeginTransmission()

      Thread.sleep(5000)

      val node1IsOk = Await.result(node1 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node2IsOk = Await.result(node2 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node3IsOk = Await.result(node3 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node4IsOk = Await.result(node4 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node5IsOk = Await.result(node5 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node6IsOk = Await.result(node6 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node7IsOk = Await.result(node7 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node8IsOk = Await.result(node8 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node9IsOk = Await.result(node9 ? ReportIn(), 1 second).asInstanceOf[Boolean]

      println(s"1: $node1IsOk")
      println(s"2: $node2IsOk")
      println(s"3: $node3IsOk")
      println(s"4: $node4IsOk")
      println(s"5: $node5IsOk")
      println(s"6: $node6IsOk")
      println(s"7: $node7IsOk")
      println(s"8: $node8IsOk")
      println(s"9: $node9IsOk")

      (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk &&
        node7IsOk && node8IsOk && node9IsOk) must be(true)

      node1 ! Kill
      node2 ! Kill
      node3 ! Kill
      node4 ! Kill
      node5 ! Kill
      node6 ! Kill
      node7 ! Kill
      node8 ! Kill
      node9 ! Kill

      system.shutdown()
      TestDvmsActor.experimentHasStarted = false
    }

    "resolve a nested deadlock (12 nodes)" in {

      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

      def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

      val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))
      val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)))
      val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)))
      val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)))
      val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)))
      val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)))
      val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)))
      val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)))
      val node9 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(9), TestDvmsFactory)))
      val node10 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(10), TestDvmsFactory)))
      val node11 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(11), TestDvmsFactory)))
      val node12 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(12), TestDvmsFactory)))


      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)
      node5 ! ConnectToThisPeerActor(node1)
      node6 ! ConnectToThisPeerActor(node1)
      node7 ! ConnectToThisPeerActor(node1)
      node8 ! ConnectToThisPeerActor(node1)
      node9 ! ConnectToThisPeerActor(node1)
      node10 ! ConnectToThisPeerActor(node1)
      node11 ! ConnectToThisPeerActor(node1)
      node12 ! ConnectToThisPeerActor(node1)


      Thread.sleep(500)


      val node1Ref = quickNodeRef(1, node1)
      val node2Ref = quickNodeRef(2, node2)
      val node3Ref = quickNodeRef(3, node3)
      val node4Ref = quickNodeRef(4, node4)
      val node5Ref = quickNodeRef(5, node5)
      val node6Ref = quickNodeRef(6, node6)
      val node7Ref = quickNodeRef(7, node7)
      val node8Ref = quickNodeRef(8, node8)
      val node9Ref = quickNodeRef(9, node9)
      val node10Ref = quickNodeRef(10, node10)
      val node11Ref = quickNodeRef(11, node11)
      val node12Ref = quickNodeRef(12, node12)

      // init the partitions
      val partition_1_5_9 = DvmsPartition(node9Ref, node1Ref, List(node1Ref, node5Ref, node9Ref), Growing())
      val partition_2_6_10 = DvmsPartition(node10Ref, node2Ref, List(node2Ref, node6Ref, node10Ref), Growing())
      val partition_3_7_11 = DvmsPartition(node11Ref, node3Ref, List(node3Ref, node7Ref, node11Ref), Growing())
      val partition_4_8_12 = DvmsPartition(node12Ref, node4Ref, List(node4Ref, node8Ref, node12Ref), Growing())


      node1 ! SetCurrentPartition(partition_1_5_9)
      node5 ! SetCurrentPartition(partition_1_5_9)
      node9 ! SetCurrentPartition(partition_1_5_9)

      node2 ! SetCurrentPartition(partition_2_6_10)
      node6 ! SetCurrentPartition(partition_2_6_10)
      node10 ! SetCurrentPartition(partition_2_6_10)

      node3 ! SetCurrentPartition(partition_3_7_11)
      node7 ! SetCurrentPartition(partition_3_7_11)
      node11 ! SetCurrentPartition(partition_3_7_11)

      node4 ! SetCurrentPartition(partition_4_8_12)
      node8 ! SetCurrentPartition(partition_4_8_12)
      node12 ! SetCurrentPartition(partition_4_8_12)

//      node1 ! SetFirstOut(node2Ref)
//      node5 ! SetFirstOut(node6Ref)
//      node9 ! SetFirstOut(node10Ref)
//
//      node2 ! SetFirstOut(node3Ref)
//      node6 ! SetFirstOut(node7Ref)
//      node10 ! SetFirstOut(node11Ref)
//
//      node3 ! SetFirstOut(node4Ref)
//      node7 ! SetFirstOut(node8Ref)
//      node11 ! SetFirstOut(node12Ref)
//
//      node4 ! SetFirstOut(node5Ref)
//      node8 ! SetFirstOut(node9Ref)
//      node12 ! SetFirstOut(node1Ref)

      // transmission of ISP to the respectives firstOuts
      TestDvmsActor.experimentHasStarted = true
      node9 ! BeginTransmission()
      node10 ! BeginTransmission()
      node11 ! BeginTransmission()
      node12 ! BeginTransmission()

      Thread.sleep(5000)

      val node1IsOk = Await.result(node1 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node2IsOk = Await.result(node2 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node3IsOk = Await.result(node3 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node4IsOk = Await.result(node4 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node5IsOk = Await.result(node5 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node6IsOk = Await.result(node6 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node7IsOk = Await.result(node7 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node8IsOk = Await.result(node8 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node9IsOk = Await.result(node9 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node10IsOk = Await.result(node10 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node11IsOk = Await.result(node11 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node12IsOk = Await.result(node12 ? ReportIn(), 1 second).asInstanceOf[Boolean]

      println(s"1: $node1IsOk")
      println(s"2: $node2IsOk")
      println(s"3: $node3IsOk")
      println(s"4: $node4IsOk")
      println(s"5: $node5IsOk")
      println(s"6: $node6IsOk")
      println(s"7: $node7IsOk")
      println(s"8: $node8IsOk")
      println(s"9: $node9IsOk")
      println(s"10: $node10IsOk")
      println(s"11: $node11IsOk")
      println(s"12: $node12IsOk")

      (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk &&
        node7IsOk && node8IsOk && node9IsOk && node10IsOk && node11IsOk && node12IsOk) must be(true)

      node1 ! Kill
      node2 ! Kill
      node3 ! Kill
      node4 ! Kill
      node5 ! Kill
      node6 ! Kill
      node7 ! Kill
      node8 ! Kill
      node9 ! Kill
      node10 ! Kill
      node11 ! Kill
      node12 ! Kill

      system.shutdown()
      TestDvmsActor.experimentHasStarted = false
    }
  }
}