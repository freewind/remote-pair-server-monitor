package com.thoughtworks.pli.remotepair.monitor

import javax.swing.DefaultListModel
import javax.swing.event.{ListSelectionEvent, ListSelectionListener, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

import com.softwaremill.quicklens._
import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.intellij.remotepair.utils.{StringOperation, StringDiff}
import com.thoughtworks.pli.remotepair.monitor.SwingVirtualImplicits._
import com.thoughtworks.pli.remotepair.monitor.models.VersionItemData
import io.netty.channel.{ChannelFuture, ChannelHandlerAdapter, ChannelHandlerContext}
import io.netty.util.concurrent.GenericFutureListener

import scala.util.Try

object MainDialog extends _MainDialog {

  private var nettyClient: Option[NettyClient] = None

  case class ServerAddress(ip: String, port: Int)

  private val parseEvent = new ParseEvent
  private var channelFuture: Option[ChannelFuture] = None

  private def connectToServer(serverAddress: ServerAddress): Unit = {
    if (nettyClient.isEmpty) nettyClient = Some(new NettyClient(serverAddress)(parseEvent))
    channelFuture = nettyClient.map { client =>
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
              case other => println("### other: " + other.toMessage)
            }
            case _ => ???
          }
        }
      })
    }
  }

  case class Change(version: Int, diffs: List[StringOperation], editorName: String)
  case class Doc(path: String, baseVersion: Int, baseContent: Content, changes: List[Change] = Nil) {
    def latestVersion: Int = changes.lastOption.map(_.version).getOrElse(baseVersion)
    def contentOfVersion(version: Int): String = StringDiff.applyOperations(baseContent.text, changes.filter(_.version <= version).flatMap(_.diffs))
  }
  case class Project(name: String, docs: List[Doc] = Nil)
  case class Projects(projects: List[Project] = Nil)

  private var projects: Projects = Projects()

  private def handleChangeContentConfirmation(projectName: String, event: ChangeContentConfirmation): Unit = {
    val change = Change(event.newVersion, event.diffs.toList, event.editorName)
    projects = projects.modify(_.projects.eachWhere(_.name == projectName).docs.eachWhere(_.path == event.path).changes)
      .using(_ ::: List(change))
    updateDisplayedSelectedDoc()
  }

  def updateDisplayedSelectedDoc(): Unit = {
    findSelectedDoc().foreach { case (projectName, doc) =>
      if (doc.changes.size > docVersionList.getModel.getSize) {
        val previousVersion = Option(docVersionList.getSelectedValue).map(_.version)
        val followChanges = docVersionList.getSelectedIndex == docVersionList.getModel.getSize - 1
        createDocVersionList(doc)
        if (followChanges) {
          selectDocVersion(doc.latestVersion)
        } else {
          previousVersion.foreach(selectDocVersion)
        }
      }
    }
  }

  private def handleCreateDocumentConfirmation(projectName: String, event: CreateDocumentConfirmation) = {
    val doc = Doc(event.path, baseVersion = event.version, baseContent = event.content)
    projects = projects.modify(_.projects.eachWhere(_.name == projectName).docs)
      .using(docs => (doc :: docs).sortBy(_.path))
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
        createDocVersionList(doc)
      }
    }
  })

  def findSelectedDoc(): Option[(String, Doc)] = {
    Option(fileTree.getSelectionPath).map(_.getLastPathComponent.asInstanceOf[DefaultMutableTreeNode].getUserObject) match {
      case Some(NodeData(projectName, docPath)) => projects.projects.find(_.name == projectName).flatMap(_.docs.find(_.path == docPath)).map((projectName, _))
      case _ => None
    }
  }

  private def createDocVersionList(doc: Doc): Unit = {
    val model = new DefaultListModel[VersionItemData]()
    model.addElement(VersionItemData(doc.baseVersion, "???"))
    doc.changes.foreach(change => model.addElement(VersionItemData(change.version, change.editorName)))
    docVersionList.setModel(model)
    docVersionList.addListSelectionListener(new ListSelectionListener {
      override def valueChanged(e: ListSelectionEvent): Unit = {
        Option(docVersionList.getSelectedValue).foreach(updateDocContentToVersion)
      }
    })
    selectDocVersion(doc.latestVersion)
  }

  private def selectDocVersion(version: Int): Unit = {
    (0 until docVersionList.getModel.getSize)
      .find(index => docVersionList.getModel.getElementAt(index).version == version)
      .foreach(docVersionList.setSelectedIndex)
  }

  private def updateDocContentToVersion(version: VersionItemData): Unit = {
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
    getInputServerAddress() match {
      case Some(serverAddress) => connectToServer(serverAddress)
      case _ => serverAddressTextField.requestFocus()
    }
  }

  closeButton.onClick {
    channelFuture.foreach(_.channel().close().sync().addListener(new GenericFutureListener[ChannelFuture] {
      override def operationComplete(future: ChannelFuture): Unit = clearAll()
    }))
  }

  private def clearAll(): Unit = {
    fileTree.setModel(null)
    println("#### docVersionList.getModel: " + docVersionList.getModel.getClass)
    docVersionList.setModel(new DefaultListModel[VersionItemData]())
    fileContentTextArea.setText("")
    filePathLabel.setText("")
  }

  def main(args: Array[String]) {
    this.pack()
    this.setSize(800, 600)
    this.setVisible(true)
    System.exit(0)
  }

}
