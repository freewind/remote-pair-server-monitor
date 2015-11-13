package com.thoughtworks.pli.remotepair.monitor

import javax.swing.{JCheckBox, JTextArea}

import com.softwaremill.quicklens._
import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.intellij.remotepair.utils.NewUuid
import com.thoughtworks.pli.remotepair.monitor.PairServerConnector.{Connected, Disconnected}
import com.thoughtworks.pli.remotepair.monitor.models._
import rx.lang.scala.{Subject, Observable}

import com.softwaremill.quicklens._
import rx.lang.scala.subjects.PublishSubject
import scala.swing.BorderPanel.Position._
import scala.swing.Orientation._
import scala.swing._
import scala.swing.event.ButtonClicked

object MyLens {
  def modifyDocEvents(projects: Projects, projectName: String, docPath: String) = {
    modify(projects)(_.projects.eachWhere(_.name == projectName).docs.eachWhere(_.path == docPath).events)
  }
  def modifyDocs(projects: Projects, projectName: String) = modify(projects)(_.projects.eachWhere(_.name == projectName).docs)
}

case class ProjectEvent[T](projectName: String, realEvent: T, timestamp: Long)

trait PairEventStreams {
  val parseEvent = new ParseEvent
  lazy val newUuid = new NewUuid
  def receivedPairEvents: Observable[PairEvent]

  val projectEvents: Observable[ProjectEvent[PairEvent]] = receivedPairEvents.collect {
    case MonitorEvent(projectName, realEventMessage, timestamp) =>
      println("### case MonitorEvent(projectName, realEventMessage, timestamp)")
      ProjectEvent(projectName, parseEvent(realEventMessage), timestamp)
  }

  val serverVersion: Observable[String] = receivedPairEvents.collect({ case ServerVersionInfo(version) => version })

  val projects: Observable[Projects] = receivedPairEvents.scan(Option.empty[Projects]) {
    case (_, ServerStatusResponse(ps, _)) => Some(Projects(ps.map(info => Project(info.name)).toList))
    case (Some(ps), MonitorEvent(projectName, realEventMessage, timestamp)) => parseEvent(realEventMessage) match {
      case CreateDocumentConfirmation(path, version, content, sourceClient) =>
        val doc = Doc(path, BaseContent(version, content, sourceClient, timestamp))
        Some(MyLens.modifyDocs(ps, projectName).using(docs => (doc :: docs).sortBy(_.path)))
      case ChangeContentConfirmation(_, path, newVersion, diffs, sourceClient) =>
        val change = ContentChange(newVersion, diffs.toList, sourceClient, timestamp, newUuid())
        Some(MyLens.modifyDocEvents(ps, projectName, path).using(_ ::: List(change)))
      case MoveCaretEvent(path, offset, sourceClient) =>
        val caret = CaretMove(offset, sourceClient, timestamp, newUuid())
        Some(MyLens.modifyDocEvents(ps, projectName, path).using(_ ::: List(caret)))
    }
    case _ => None
  }.collect({ case Some(p) => p })

  val projectNames: Observable[Seq[String]] = projects.map(_.projects.map(_.name))

  projectNames.foreach {
    names => println("project names: " + names)
  }

  val selectedProjectNames: Subject[Seq[String]] = PublishSubject[Seq[String]]()

  val selectedProjects: Observable[Seq[Project]] = selectedProjectNames.combineLatestWith(projects) {
    (names, projects) => names.flatMap(name => projects.projects.find(_.name == name))
  }

}


object NewDialog extends SimpleSwingApplication with PairEventStreams {

  lazy val pairServerConnector = new PairServerConnector

  override def receivedPairEvents = pairServerConnector.receivedEvents

  pairServerConnector.connectorEvents.foreach {
    case Connected(conn) => conn.write(ImMonitor.toMessage)
    case Disconnected => ???
  }

  override def top: Frame = new MainFrame {frame =>
    title = "monitor"
    minimumSize = new Dimension(800, 600)

    contents = new BorderPanel {

      private val connectionPanel = new BoxPanel(Horizontal) {
        lazy val serverAddress = new TextField("localhost:8888")
        contents += new Label("Server address")
        contents += serverAddress
        contents += new Button("Connect") {
          reactions += { case _ => ServerAddress.parse(serverAddress.text).foreach(pairServerConnector.connect) }
        }
        contents += new Button("X") {
          reactions += { case _ => pairServerConnector.disconnect() }
        }
        contents += new Label("server version: ")
        contents += new Label("???") {
          serverVersion.map("server version: " + _).foreach(text = _)
        }
      }

      lazy val monitorPanel = new BoxPanel(Horizontal) {
        var simpleMonitors = Seq.empty[ProjectMonitor]

        selectedProjects map { projects =>
          val existingProjects = simpleMonitors.map(_.project)
          (projects.filterNot(existingProjects.contains), existingProjects.filterNot(projects.contains))
        } map { case (newSelected, removed) =>
          newSelected.map(new ProjectMonitor(_)) ++ simpleMonitors.filterNot(m => removed.contains(m.project))
        } foreach { newMonitors =>
          simpleMonitors = newMonitors

          contents.clear()
          contents ++= newMonitors

          frame.pack()
          frame.repaint()
        }
      }

      lazy val controlPanel = new BoxPanel(Horizontal) {
        var controls = Seq.empty[ProjectNameControl]

        projectNames.map(names => {
          val existingNames = controls.map(_.projectName)
          (names.filterNot(existingNames.contains), existingNames.filterNot(names.contains))
        }).map { case (newNames, removedNames) =>
          newNames.map(new ProjectNameControl(_)) ++ controls.filterNot(ctrl => removedNames.contains(ctrl.projectName))
        }.foreach { newControls =>
          controls = newControls

          contents.clear()
          contents ++= newControls

          frame.pack()
          frame.repaint()

          listenTo(newControls: _*)
          reactions += {
            case ButtonClicked(_) => selectedProjectNames.onNext(newControls.filter(_.selected).map(_.text))
          }
        }
      }

      layout(connectionPanel) = North
      layout(monitorPanel) = Center
      layout(controlPanel) = South
    }
  }


}


class ProjectNameControl(val projectName: String) extends CheckBox(projectName) {

}
