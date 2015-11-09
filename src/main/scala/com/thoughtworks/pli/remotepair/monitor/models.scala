package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.intellij.remotepair.protocol.Content
import com.thoughtworks.pli.intellij.remotepair.server.ClientIdName
import com.thoughtworks.pli.intellij.remotepair.utils.{StringDiff, StringOperation}

object models {

  case class Projects(projects: List[Project] = Nil)
  case class Project(name: String, docs: List[Doc] = Nil)
  case class Doc(path: String, baseVersion: Int, baseContent: Content, baseSourceClient: ClientIdName, events: List[DocEvent] = Nil) {
    def contentChanges: List[ContentChange] = events.collect({ case e: ContentChange => e })
    def latestVersion: Int = contentChanges.lastOption.map(_.version).getOrElse(baseVersion)
    def contentOfVersion(version: Int): String = StringDiff.applyOperations(baseContent.text, contentChanges.filter(_.version <= version).flatMap(_.diffs))
  }
  trait DocEvent
  case class ContentChange(version: Int, diffs: List[StringOperation], sourceClient: ClientIdName) extends DocEvent

  case class DocEventItemData(version: Int, sourceClient: ClientIdName) {
    override def toString: String = sourceClient.name + ": " + version.toString
  }

}
