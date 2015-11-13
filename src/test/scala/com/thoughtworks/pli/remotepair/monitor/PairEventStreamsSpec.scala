package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.intellij.remotepair.server.ClientIdName
import com.thoughtworks.pli.intellij.remotepair.utils.{NewUuid, Delete, Insert}
import com.thoughtworks.pli.remotepair.monitor.models._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import rx.lang.scala.Observable

class PairEventStreamsSpec extends Specification with Mockito {

  isolated

  val serverStatusResponse = new ServerStatusResponse(Seq(ProjectInfoData("p1", Nil, Nil, WorkingMode.CaretSharing), ProjectInfoData("p2", Nil, Nil, WorkingMode.CaretSharing)), 0)
  val createDocumentConfirmation = new MonitorEvent("p1", CreateDocumentConfirmation("/aaa", 1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1")).toMessage, 111111)
  val changeContentConfirmation = new MonitorEvent("p1", ChangeContentConfirmation("event-id1", "/aaa", 1, Seq(Insert(1, "kk"), Delete(10, 3)), ClientIdName("client-id2", "client-name2")).toMessage, 222222)
  val moveCaretEvent = new MonitorEvent("p1", new MoveCaretEvent("/aaa", 10, ClientIdName("client-id3", "client-name3")).toMessage, 333333)

  "projectEvents" >> {
    val streams = new PairEventStreams {
      override def receivedPairEvents = Observable.just(moveCaretEvent)
    }
    streams.projectEvents.toBlocking.toList ==== List(new ProjectEvent("p1", new MoveCaretEvent("/aaa", 10, ClientIdName("client-id3", "client-name3")), 333333))
  }

  "projects" >> {
    "ServerStatusResponse" >> {
      val streams = new PairEventStreams {
        override def receivedPairEvents = Observable.just(serverStatusResponse)
      }
      streams.projects.toBlocking.toList ==== List(Projects(List(Project("p1"), Project("p2"))))
    }
    "ServerStatusResponse & CreateDocumentConfirmation" >> {
      val streams = new PairEventStreams {
        override def receivedPairEvents = Observable.just(serverStatusResponse, createDocumentConfirmation)
      }
      streams.projects.toBlocking.toList.last ==== Projects(List(
        Project("p1", List(Doc("/aaa", BaseContent(1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1"), 111111)))),
        Project("p2")))
    }
    "ServerStatusResponse & CreateDocumentConfirmation & ChangeContentConfirmation" >> {
      val streams = new PairEventStreams {
        override def receivedPairEvents = Observable.just(serverStatusResponse, createDocumentConfirmation, changeContentConfirmation, moveCaretEvent)
        override lazy val newUuid: NewUuid = mock[NewUuid]
        newUuid.apply returns "uuid1"
      }
      streams.projects.toBlocking.toList.last ==== Projects(List(
        Project("p1", List(Doc("/aaa", BaseContent(1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1"), 111111), List(
          ContentChange(1, List(Insert(1, "kk"), Delete(10, 3)), ClientIdName("client-id2", "client-name2"), 222222, "uuid1"),
          CaretMove(10, ClientIdName("client-id3", "client-name3"), 333333, "uuid1")
        )))),
        Project("p2")))
    }
  }

}
