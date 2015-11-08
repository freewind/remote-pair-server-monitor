package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.intellij.remotepair.protocol.Content
import com.thoughtworks.pli.intellij.remotepair.server.ClientIdName
import com.thoughtworks.pli.intellij.remotepair.utils.{StringDiff, StringOperation}

object models {

  case class Projects(projects: List[Project] = Nil)
  case class Project(name: String, docs: List[Doc] = Nil)
  case class Doc(path: String, baseVersion: Int, baseContent: Content, baseSourceClient: ClientIdName, changes: List[Change] = Nil) {
    def latestVersion: Int = changes.lastOption.map(_.version).getOrElse(baseVersion)
    def contentOfVersion(version: Int): String = StringDiff.applyOperations(baseContent.text, changes.filter(_.version <= version).flatMap(_.diffs))
  }
  case class Change(version: Int, diffs: List[StringOperation], sourceClient: ClientIdName)

  case class VersionItemData(version: Int, sourceClient: ClientIdName) {
    override def toString: String = sourceClient.name + ": " + version.toString
  }

}
