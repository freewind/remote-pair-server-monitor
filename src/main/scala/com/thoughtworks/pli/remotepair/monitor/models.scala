package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.intellij.remotepair.protocol.Content
import com.thoughtworks.pli.intellij.remotepair.server.ClientIdName
import com.thoughtworks.pli.intellij.remotepair.utils.{NewUuid, StringDiff, StringOperation}

import scalaz._

object models {

  case class Projects(projects: List[Project] = Nil)
  case class Project(name: String, docs: List[Doc] = Nil)
  case class Doc(path: String, baseContent: BaseContent, events: List[DocEvent] = Nil) {
    def contentChanges: List[ContentChange] = events.collect({ case e: ContentChange => e })
    def latestVersion: Int = contentChanges.lastOption.map(_.version).getOrElse(baseContent.version)
    def contentAtEvent(eventId: String): String = {
      val diffs = (events.takeWhile(_.id != eventId) ::: events.find(_.id == eventId).toList).collect({ case e: ContentChange => e }).flatMap(_.diffs)
      StringDiff.applyOperations(baseContent.content.text, diffs)
    }
    def caretAtEvent(eventId: String): Option[Int] = {
      (events.takeWhile(_.id != eventId) ::: events.find(_.id == eventId).toList).collect({ case e: CaretMove => e }).lastOption.map(_.offset)
    }
  }
  case class BaseContent(version: Int, content: Content, sourceClient: ClientIdName)

  sealed trait DocEvent {
    val id: String
  }

  case class ContentChange(version: Int, diffs: List[StringOperation], sourceClient: ClientIdName, id: String = new NewUuid().apply()) extends DocEvent
  case class CaretMove(offset: Int, sourceClient: ClientIdName, id: String = new NewUuid().apply()) extends DocEvent

  case class DocEventItemData(data: BaseContent \/ DocEvent) {
    override def toString: String = data match {
      case -\/(BaseContent(version, Content(_, charset), ClientIdName(_, name))) => s"[$name] version: $version, charset: $charset"
      case \/-(ContentChange(version, _, ClientIdName(_, name), _)) => s"[$name] version: $version"
      case \/-(CaretMove(offset, ClientIdName(_, name), _)) => s"[$name] caret: $offset"
    }
  }

}
