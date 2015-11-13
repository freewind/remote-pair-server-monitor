package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.remotepair.monitor.models.{Doc, DocEventItemData, Project}

import scala.swing.Orientation._
import scala.swing.{BorderPanel, BoxPanel, ListView}
import scalaz.{-\/, \/-}

class ProjectDetailComponent(project: Project) extends BoxPanel(Horizontal) {

  val selectedDoc: Option[Doc] = findLatestDoc

  contents ++= Seq(
    new ListView(project.docs.map(_.path)),
    new Editor {
    },
    new ListView(selectedDoc.map(getListItems).getOrElse(Nil))
  )

  private def findLatestDoc: Option[Doc] = {
    project.docs.sortBy(doc => doc.contentChanges.map(_.timestamp).lastOption.getOrElse(doc.baseContent.timestamp)).lastOption
  }

  private def getListItems(doc: Doc): List[DocEventItemData] = {
    DocEventItemData(-\/(doc.baseContent)) :: doc.events.map(e => DocEventItemData(\/-(e)))
  }
}

