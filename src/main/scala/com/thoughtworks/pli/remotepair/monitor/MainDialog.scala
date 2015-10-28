package com.thoughtworks.pli.remotepair.monitor

import javax.swing.DefaultListModel
import javax.swing.event.{ListSelectionEvent, ListSelectionListener, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.intellij.remotepair.utils.{ContentDiff, StringDiff}
import com.thoughtworks.pli.remotepair.monitor.SwingVirtualImplicits._
import com.thoughtworks.pli.remotepair.monitor.models.VersionNodeData
import io.netty.channel.{ChannelHandlerAdapter, ChannelHandlerContext}
import monocle.function.Each._
import monocle.macros.Lenses
import monocle.std.list._

import scala.util.Try

object MainDialog extends _MainDialog {

  private var nettyClient: Option[NettyClient] = None

  case class ServerAddress(ip: String, port: Int)

  private val parseEvent = new ParseEvent

  private def connectToServer(serverAddress: ServerAddress): Unit = {
    if (nettyClient.isEmpty) nettyClient = Some(new NettyClient(serverAddress)(parseEvent))
    nettyClient.foreach { client =>
      client.connect(new ChannelHandlerAdapter {
        override def channelActive(ctx: ChannelHandlerContext): Unit = {
          ctx.writeAndFlush(ImMonitor.toMessage)
        }
        override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
          msg match {
            case event: ServerVersionInfo => handleServerVersionInfo(event)
            case event: ServerStatusResponse => handleServerStatusResponse(event)
            case MonitorEvent(projectName, realEventMessage, _) => parseEvent(realEventMessage) match {
              case event: CreateDocumentConfirmation => handleCreateDocumentConfirmation(projectName, event)
              case event: ChangeContentConfirmation => handleChangeContentConfirmation(projectName, event)
              case other => println("### s" + other.toMessage)
            }
            case _ => ???
          }
        }
      })
    }
  }

  @Lenses("_") case class Change(version: Int, diffs: List[ContentDiff])
  @Lenses("_") case class Doc(path: String, baseVersion: Int, baseContent: Content, changes: List[Change] = Nil) {
    def contentOfVersion(version: Int): String = StringDiff.applyDiffs(baseContent.text, changes.filter(_.version <= version).flatMap(_.diffs))
  }
  @Lenses("_") case class Project(name: String, docs: List[Doc] = Nil)
  @Lenses("_") case class Projects(projects: List[Project] = Nil)

  import Project._
  import Projects._


  private var projects: Projects = Projects()

  private def handleChangeContentConfirmation(projectName: String, event: ChangeContentConfirmation): Unit = {
    val change = Change(event.newVersion, event.diffs.toList)
    projects = (_projects composeTraversal each).modify(project => {
      if (project.name == projectName) {
        (_docs composeTraversal each).modify(doc => {
          if (doc.path == event.path) doc.copy(changes = doc.changes ::: List(change)) else doc
        })(project)
      } else project
    })(projects)
  }

  private def handleCreateDocumentConfirmation(projectName: String, event: CreateDocumentConfirmation) = {
    val doc = Doc(event.path, baseVersion = event.version, baseContent = event.content)
    val trans = (_projects composeTraversal each).modify(project => {
      if (project.name == projectName) _docs.modify(docs => (doc :: docs).sortBy(_.path))(project) else project
    })
    projects = trans(projects)
    createTree()
  }

  private def handleServerVersionInfo(event: ServerVersionInfo): Unit = {
    serverVersionLabel.setText("server version: " + event.version)
  }

  private def handleServerStatusResponse(event: ServerStatusResponse): Unit = {
    projects = Projects(event.projects.map(info => Project(info.name)).toList)
    createTree()
  }

  case class NodeData(projectName: String, docPath: String) {
    override def toString: String = docPath
  }

  private def createTree(): Unit = {
    val root = new DefaultMutableTreeNode("projects")
    projects.projects.foreach(project => {
      val pNode = new DefaultMutableTreeNode(project.name)
      project.docs.foreach(doc => pNode.add(new DefaultMutableTreeNode(NodeData(project.name, doc.path))))
      root.add(pNode)
    })
    fileTree.setModel(new DefaultTreeModel(root))
    fileTree.updateUI()
  }


  fileTree.addTreeSelectionListener(new TreeSelectionListener {
    override def valueChanged(e: TreeSelectionEvent): Unit = {
      findSelectedDoc().foreach { case (projectName, doc) =>
        filePathLabel.setText(doc.path)
        showDocVersionList(doc)
      }
    }
  })

  def findSelectedDoc(): Option[(String, Doc)] = {
    fileTree.getSelectionPath.getLastPathComponent.asInstanceOf[DefaultMutableTreeNode].getUserObject match {
      case NodeData(projectName, docPath) => projects.projects.find(_.name == projectName).flatMap(_.docs.find(_.path == docPath)).map((projectName, _))
      case _ => None
    }
  }

  def showDocVersionList(doc: Doc): Unit = {
    val model = new DefaultListModel[VersionNodeData]()
    doc.changes.foreach(change => model.addElement(VersionNodeData(change.version)))
    fileVersionList.setModel(model)
    fileVersionList.addListSelectionListener(new ListSelectionListener {
      override def valueChanged(e: ListSelectionEvent): Unit = {
        Option(fileVersionList.getSelectedValue).foreach(updateDocContentToVersion)
      }
    })
    fileVersionList.setSelectedIndex(model.getSize - 1)
  }

  private def updateDocContentToVersion(version: VersionNodeData): Unit = {
    findSelectedDoc().foreach { case (_, doc) =>
      fileContentTextArea.setText(doc.contentOfVersion(version.version))
    }
  }

  def findDoc(projectName: String, docPath: String): Option[Doc] = {
    projects.projects.find(_.name == projectName).flatMap(_.docs.find(_.path == docPath))
  }

  private def getInputServerAddress(): Option[ServerAddress] = {
    serverAddressTextField.trimmedText.split(":") match {
      case Array(ip, port) => Try(ServerAddress(ip, port.toInt)).toOption
      case _ => None
    }
  }

  connectButton.onClick {
    println("### clicked on connectButton")
    getInputServerAddress() match {
      case Some(serverAddress) => connectToServer(serverAddress)
      case _ => serverAddressTextField.requestFocus()
    }
  }

  def main(args: Array[String]) {
    this.pack()
    this.setVisible(true)
    System.exit(0)
  }

}
