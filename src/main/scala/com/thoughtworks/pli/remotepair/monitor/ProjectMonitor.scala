package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.remotepair.monitor.models.{Doc, DocEventItemData, Project}

import scala.swing.{ListView, BorderPanel}

class ProjectMonitor(val project: Project) extends BorderPanel {

  import BorderPanel.Position._

  println("############# project")
  project.docs.foreach(println)
  private lazy val fileList = new ListView[Doc](project.docs)
  private lazy val editor = new Editor() {
    text = "this is code"
  }
  private lazy val eventList = new ListView(Seq("version 0", "version 1"))

  layout(fileList) = West
  layout(editor) = Center
  layout(eventList) = East

}

class FileList extends ListView[String] {

}

class DocEventList extends ListView[DocEventItemData] {

}



