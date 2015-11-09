package com.thoughtworks.pli.remotepair.monitor

import javax.swing.{JList, DefaultListModel}
import javax.swing.event.{ListSelectionEvent, ListSelectionListener, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

import com.softwaremill.quicklens._
import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.remotepair.monitor.SwingVirtualImplicits._
import com.thoughtworks.pli.remotepair.monitor.models._
import io.netty.channel.{ChannelFuture, ChannelHandlerAdapter, ChannelHandlerContext}
import io.netty.util.concurrent.GenericFutureListener

import scala.util.Try

object MainDialog extends _MainDialog {

  trait DialogOperations {
    def updateServerVersionLabel(version: String): Unit = {
      serverVersionLabel.setText(version)
    }
  }

  object DialogOperations extends DialogOperations

  implicit class RichJList[E](list: JList[E]) {
    def itemCount = list.getModel.getSize
    def isSelectedOnLastItem = itemCount == list.getSelectedIndex + 1
  }

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


  private var projects: Projects = Projects()

  private def handleChangeContentConfirmation(projectName: String, event: ChangeContentConfirmation): Unit = {
    val change = ContentChange(event.newVersion, event.diffs.toList, event.sourceClient)
    projects = modifyDocEvents(projects, projectName, event.path).using(_ ::: List(change))
    selectedDoc.find(_.path == event.path).foreach(renderDoc)
  }

  def modifyDocEvents(projects: Projects, projectName: String, docPath: String) = {
    modify(projects)(_.projects.eachWhere(_.name == projectName).docs.eachWhere(_.path == docPath).events)
  }

  def selectedDoc: Option[Doc] = findSelectedDoc().map(_.doc)

  def renderDoc(doc: Doc): Unit = {
    val previousVersion = Option(docEventList.getSelectedValue).map(_.version)
    val followChanges = docEventList.isSelectedOnLastItem

    createDocVersionList(doc)

    if (followChanges) selectDocVersion(doc.latestVersion) else previousVersion.foreach(selectDocVersion)
  }

  private def handleCreateDocumentConfirmation(projectName: String, event: CreateDocumentConfirmation) = {
    val doc = Doc(event.path, event.version, event.content, event.sourceClient)
    projects = modifyDocs(projects, projectName).using(docs => (doc :: docs).sortBy(_.path))
    createTree()
  }

  private def modifyDocs(projects: Projects, projectName: String) = modify(projects)(_.projects.eachWhere(_.name == projectName).docs)

  private def handleServerVersionInfo(event: ServerVersionInfo): Unit = {
    DialogOperations.updateServerVersionLabel(s"server version: ${event.version}")
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
      findSelectedDoc().foreach { case DocInProject(projectName, doc) =>
        filePathLabel.setText(doc.path)
        createDocVersionList(doc)
      }
    }
  })


  case class DocInProject(projectName: String, doc: Doc)

  def findSelectedDoc(): Option[DocInProject] = {
    Option(fileTree.getSelectionPath).map(_.getLastPathComponent.asInstanceOf[DefaultMutableTreeNode].getUserObject) match {
      case Some(NodeData(projectName, docPath)) => projects.projects.find(_.name == projectName).flatMap(_.docs.find(_.path == docPath)).map(DocInProject(projectName, _))
      case _ => None
    }
  }

  private def createDocVersionList(doc: Doc): Unit = {
    val model = new DefaultListModel[DocEventItemData]()
    model.addElement(DocEventItemData(doc.baseVersion, doc.baseSourceClient))
    doc.contentChanges.foreach(change => model.addElement(DocEventItemData(change.version, change.sourceClient)))
    docEventList.setModel(model)
    docEventList.addListSelectionListener(new ListSelectionListener {
      override def valueChanged(e: ListSelectionEvent): Unit = {
        Option(docEventList.getSelectedValue).foreach(updateDocContentToVersion)
      }
    })
    selectDocVersion(doc.latestVersion)
  }

  private def selectDocVersion(version: Int): Unit = {
    (0 until docEventList.getModel.getSize)
      .find(index => docEventList.getModel.getElementAt(index).version == version)
      .foreach(docEventList.setSelectedIndex)
  }

  private def updateDocContentToVersion(version: DocEventItemData): Unit = {
    findSelectedDoc().foreach { case DocInProject(_, doc) =>
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
    println("#### docEventList.getModel: " + docEventList.getModel.getClass)
    docEventList.setModel(new DefaultListModel[DocEventItemData]())
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
