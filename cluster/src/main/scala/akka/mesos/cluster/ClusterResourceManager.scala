package akka.mesos.cluster

import akka.actor._
import akka.mesos.AkkaMesosScheduler
import akka.mesos.AkkaMesosScheduler._
import akka.cluster.Cluster
import org.apache.mesos.Protos.{ TaskInfo, OfferID }
import java.util
import akka.contrib.pattern.ClusterSingletonManager
import akka.mesos.AkkaMesosScheduler.ResourceOffers
import scala.Some
import java.net.InetAddress

private[cluster] class ClusterResourceManager extends Actor with AkkaMesosScheduler {
  import ClusterResourceManager._

  var receivers = Set.empty[ActorRef]

  override def preStart = register()

  def receive = {
    case Subscribe(receiver) =>
      receivers += receiver

    case ClaimResource(id) =>
      log.info(s"Claiming resource $id")
    // TODO: start new akka node

    case ResourceOffers(offers) =>
      if (receivers.nonEmpty) {
        // TODO: retrieve canonical hostname to unify names from mesos and akka
        val members = Cluster(context.system).state.members.map(_.address.host.getOrElse(""))
        log.info(s"$members")
        val newHostOffers = offers.filterNot(o => members(o.getHostname))
        val offer = ResourceOffers(newHostOffers)

        receivers.foreach(_ ! offer)
      }
      else {
        log.info("Declining offers because no receivers are subscribed.")
        offers.foreach(o => driver.declineOffer(o.getId))
      }

    case x => log.info(s"Received unknown message $x")
  }
}

object ClusterResourceManager {
  // TODO: create the nodeStartupTask
  //private[ClusterResourceManager] val nodeStartupTask = util.Arrays.asList(TaskInfo.newBuilder().build())

  case class Subscribe(receiver: ActorRef)
  case class ClaimResource(id: OfferID)

  // TODO: remove when not needed anymore
  def main(args: Array[String]): Unit = {
    if (args.nonEmpty) System.setProperty("akka.remote.netty.tcp.port", args(0))

    val system = ActorSystem("ClusterSystem")

    Cluster(system).registerOnMemberUp {
      val manager = system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props[ClusterResourceManager],
          singletonName = "ClusterResourceManager",
          terminationMessage = PoisonPill,
          role = None
        ),
        name = "singleton"
      )

      /*
      val starter = system.actorOf(Props[MyTaskStarter])

      manager ! Subscribe(starter)
      */
    }
  }

  // TODO: remove when not needed anymore
  class MyTaskStarter extends Actor {
    def receive = {
      case ResourceOffers(offers) =>
        offers.foreach(o => sender ! ClaimResource(o.getId))
    }
  }
}
