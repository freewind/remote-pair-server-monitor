package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.intellij.remotepair.server.ClientIdName
import com.thoughtworks.pli.intellij.remotepair.utils.{Delete, Insert, NewUuid}
import com.thoughtworks.pli.remotepair.monitor.models._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import rx.lang.scala.observers.TestSubscriber
import rx.lang.scala.subjects.PublishSubject
import rx.lang.scala.{Observable, Subject}

class PairEventStreamsSpec extends Specification with Mockito with NoTimeConversions {

  isolated

  val newUuidMock = mock[NewUuid]
  newUuidMock.apply returns "uuid1" thenReturns "uuid2"

  val serverStatusResponse = new ServerStatusResponse(Seq(ProjectInfoData("p1", Nil, Nil, WorkingMode.CaretSharing), ProjectInfoData("p2", Nil, Nil, WorkingMode.CaretSharing)), 0)
  val createDocumentConfirmation = new MonitorEvent("p1", CreateDocumentConfirmation("/aaa", 1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1")).toMessage, 111111)
  val changeContentConfirmation = new MonitorEvent("p1", ChangeContentConfirmation("event-id1", "/aaa", 1, Seq(Insert(1, "kk"), Delete(10, 3)), ClientIdName("client-id2", "client-name2")).toMessage, 222222)
  val moveCaretEvent = new MonitorEvent("p1", new MoveCaretEvent("/aaa", 10, ClientIdName("client-id3", "client-name3")).toMessage, 333333)

  "projectEvents" >> {
    val testSubscriber = TestSubscriber[ProjectEvent[PairEvent]]()
    "should be converted from `MonitorEvent` of MoveCaretEvent" >> {
      new PairEventStreams {
        override val receivedPairEvents = Observable.just(moveCaretEvent)
        override val selectedProjectNames: Observable[Seq[String]] = Observable.never // dummy
        projectEvents.subscribe(testSubscriber)
      }
      testSubscriber.getOnNextEvents.toList ==== List(new ProjectEvent("p1", new MoveCaretEvent("/aaa", 10, ClientIdName("client-id3", "client-name3")), 333333))
    }
    "should only be converted and collected from `MonitorEvent` of MoveCaretEvent" >> {
      val nonProjectEvents = Observable.just(serverStatusResponse)
      val monitorEvents = Observable.just(createDocumentConfirmation, changeContentConfirmation, moveCaretEvent)

      new PairEventStreams {
        override val receivedPairEvents = nonProjectEvents ++ monitorEvents
        override val selectedProjectNames: Observable[Seq[String]] = Observable.never // dummy
        projectEvents.subscribe(testSubscriber)
      }

      testSubscriber.getOnNextEvents.toList ==== List(
        new ProjectEvent("p1", new CreateDocumentConfirmation("/aaa", 1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1")), 111111),
        new ProjectEvent("p1", new ChangeContentConfirmation("event-id1", "/aaa", 1, Seq(Insert(1, "kk"), Delete(10, 3)), ClientIdName("client-id2", "client-name2")), 222222),
        new ProjectEvent("p1", new MoveCaretEvent("/aaa", 10, ClientIdName("client-id3", "client-name3")), 333333)
      )
    }
  }

  "projects" >> {
    val testSubscriber = TestSubscriber[Projects]()
    "ServerStatusResponse" >> {
      new PairEventStreams {
        override val receivedPairEvents = Observable.just(serverStatusResponse)
        override val selectedProjectNames: Observable[Seq[String]] = Observable.never // dummy
        projects.subscribe(testSubscriber)
      }
      testSubscriber.getOnNextEvents.toList ==== List(Projects(List(Project("p1"), Project("p2"))))
    }
    "ServerStatusResponse & CreateDocumentConfirmation" >> {
      new PairEventStreams {
        override val receivedPairEvents = Observable.just(serverStatusResponse, createDocumentConfirmation)
        override val selectedProjectNames: Observable[Seq[String]] = Observable.never // dummy
        projects.subscribe(testSubscriber)
      }
      testSubscriber.getOnNextEvents.toList.last ==== Projects(List(
        Project("p1", List(Doc("/aaa", BaseContent(1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1"), 111111)))),
        Project("p2")))
    }
    "ServerStatusResponse & CreateDocumentConfirmation & ChangeContentConfirmation" >> {
      new PairEventStreams {
        override val receivedPairEvents = Observable.just(serverStatusResponse, createDocumentConfirmation, changeContentConfirmation, moveCaretEvent)
        override val selectedProjectNames: Observable[Seq[String]] = Observable.never
        override lazy val newUuid: NewUuid = newUuidMock
        projects.subscribe(testSubscriber)
      }

      testSubscriber.getOnNextEvents.toList.last ==== Projects(List(
        Project("p1", List(Doc("/aaa", BaseContent(1, Content("my-content", "UTF-8"), ClientIdName("client-id1", "client-name1"), 111111), List(
          ContentChange(1, List(Insert(1, "kk"), Delete(10, 3)), ClientIdName("client-id2", "client-name2"), 222222, "uuid1"),
          CaretMove(10, ClientIdName("client-id3", "client-name3"), 333333, "uuid2")
        )))),
        Project("p2")))
    }
  }

  "selectedProjects should pop new project" >> {
    val testSubscriber = TestSubscriber[Seq[Project]]()

    "if selected project names changed" >> {
      val namesSubject = PublishSubject[Seq[String]]
      new PairEventStreams {
        override val receivedPairEvents = Observable.just(serverStatusResponse)
        override lazy val selectedProjectNames: Subject[Seq[String]] = namesSubject
        override lazy val newUuid: NewUuid = newUuidMock
        selectedProjects.subscribe(testSubscriber)
      }

      namesSubject.onNext(Seq("p1"))
      namesSubject.onNext(Seq("p2"))
      testSubscriber.getOnNextEvents.toList ==== List(Seq(Project("p1")), Seq(Project("p2")))
    }
    "only if project names changed" >> {
      val receivedPairEventsSubject = PublishSubject[PairEvent]
      val selectedProjectNamesSubject = PublishSubject[Seq[String]]

      new PairEventStreams {
        override val receivedPairEvents: Observable[PairEvent] = receivedPairEventsSubject
        override lazy val selectedProjectNames: Observable[Seq[String]] = selectedProjectNamesSubject
        override lazy val newUuid: NewUuid = newUuidMock
        selectedProjects.subscribe(testSubscriber)
      }

      receivedPairEventsSubject.onNext(serverStatusResponse)
      selectedProjectNamesSubject.onNext(Seq("p1"))

      // this event won't change 'selectedProjects'
      receivedPairEventsSubject.onNext(createDocumentConfirmation)

      testSubscriber.getOnNextEvents.toList ==== List(Seq(Project("p1")))
    }
  }

}
