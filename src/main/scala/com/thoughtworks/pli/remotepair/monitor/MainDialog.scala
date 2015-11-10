package com.thoughtworks.pli.remotepair.monitor

import com.softwaremill.quicklens._
import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.remotepair.monitor.MainDialog._
import com.thoughtworks.pli.remotepair.monitor.SwingVirtualImplicits._
import com.thoughtworks.pli.remotepair.monitor.models._
import io.netty.channel.{ChannelFuture, ChannelHandlerAdapter, ChannelHandlerContext}
import io.netty.util.concurrent.GenericFutureListener

import scala.util.Try
import scalaz._

object X {
  def modifyDocEvents(projects: Projects, projectName: String, docPath: String) = {
    modify(projects)(_.projects.eachWhere(_.name == projectName).docs.eachWhere(_.path == docPath).events)
  }
  def modifyDocs(projects: Projects, projectName: String) = modify(projects)(_.projects.eachWhere(_.name == projectName).docs)

}

trait VirtualDialog {
  val serverVersionLabel: RichLabel
  val filePathLabel: RichLabel
  val fileContentTextArea: RichTextArea
  val serverAddressTextField: RichTextField
  val docEventList: RichList[DocEventItemData]
  val fileTree: RichTree
  val connectButton: RichButton
  val closeButton: RichButton

  private var channelFuture: Option[ChannelFuture] = None
  private var nettyClient: Option[NettyClient] = None
  private val parseEvent = new ParseEvent
  private var projects: Projects = Projects()

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
              case event: MoveCaretEvent => handleMoveCaretEvent(projectName, event)
              case other => println("### other: " + other.toMessage)
            }
            case _ => ???
          }
        }
      })
    }
  }

  private def handleMoveCaretEvent(projectName: String, event: MoveCaretEvent): Unit = {
    val caret = CaretMove(event.offset, event.sourceClient)
    projects = X.modifyDocEvents(projects, projectName, event.path).using(_ ::: List(caret))
    selectedDoc.find(_.path == event.path).foreach(renderDoc)
  }

  private def handleChangeContentConfirmation(projectName: String, event: ChangeContentConfirmation): Unit = {
    val change = ContentChange(event.newVersion, event.diffs.toList, event.sourceClient)
    projects = X.modifyDocEvents(projects, projectName, event.path).using(_ ::: List(change))
    selectedDoc.find(_.path == event.path).foreach(renderDoc)
  }


  def selectedDoc: Option[Doc] = findSelectedDoc().map(_.doc)

  def renderDoc(doc: Doc): Unit = {
    val previousItem = Option(docEventList.getSelectedValue)
    val followChanges = docEventList.isSelectedOnLastItem

    createDocEventList(doc)

    if (followChanges) docEventList.selectLastItem() else previousItem.foreach(docEventList.selectItem)
  }

  private def handleCreateDocumentConfirmation(projectName: String, event: CreateDocumentConfirmation) = {
    val doc = Doc(event.path, BaseContent(event.version, event.content, event.sourceClient))
    projects = X.modifyDocs(projects, projectName).using(docs => (doc :: docs).sortBy(_.path))
    createTree()
  }

  private def handleServerVersionInfo(event: ServerVersionInfo): Unit = {
    serverVersionLabel.text = s"server version: ${event.version}"
  }

  private def handleServerStatusResponse(event: ServerStatusResponse): Unit = {
    projects = Projects(event.projects.map(info => Project(info.name)).toList)
    createTree()
  }

  case class NodeData(projectName: String, docPath: String) {
    override def toString: String = docPath
  }

  private def createTree(): Unit = {
    val tree = Branch("projects", projects.projects.map { project =>
      Option(project.docs).filterNot(_.isEmpty)
        .map(docs => Branch(project.name, docs.map(doc => Leaf(NodeData(project.name, doc.path)))))
        .getOrElse(Leaf(project.name))
    })
    fileTree.setNodes(tree)
  }

  def init(): Unit = {
    fileTree.onSelect {
      findSelectedDoc().foreach { case DocInProject(projectName, doc) =>
        val docPath = doc.path
        filePathLabel.text = docPath
        createDocEventList(doc)
      }
    }

    connectButton.onClick {
      getInputServerAddress() match {
        case Some(serverAddress) => connectToServer(serverAddress)
        case _ => _serverAddressTextField.requestFocus()
      }
    }

    closeButton.onClick {
      channelFuture.foreach(_.channel().close().sync().addListener(new GenericFutureListener[ChannelFuture] {
        override def operationComplete(future: ChannelFuture): Unit = clearAll()
      }))
    }
  }

  case class DocInProject(projectName: String, doc: Doc)

  def findSelectedDoc(): Option[DocInProject] = {
    fileTree.getSelectedUserObject match {
      case Some(NodeData(projectName, docPath)) => projects.projects.find(_.name == projectName).flatMap(_.docs.find(_.path == docPath)).map(DocInProject(projectName, _))
      case _ => None
    }
  }

  private def createDocEventList(doc: Doc): Unit = {
    docEventList.items = DocEventItemData(-\/(doc.baseContent)) :: doc.events.map(e => DocEventItemData(\/-(e)))
    docEventList.onSelect(() => Option(docEventList.getSelectedValue).foreach(updateDocContentToItem))
    docEventList.selectLastItem()
  }

  private def updateDocContentToItem(itemData: DocEventItemData): Unit = {
    findSelectedDoc().foreach { case DocInProject(_, doc) =>
      val (newContent, caret) = itemData.data.map(_.id) match {
        case -\/(baseContent) => (baseContent.content.text, None)
        case \/-(eventId) => (doc.contentAtEvent(eventId), doc.caretAtEvent(eventId))
      }
      fileContentTextArea.setText(newContent, caret.map(_ + 1))
    }
  }

  def findDoc(projectName: String, docPath: String): Option[Doc] = {
    projects.projects.find(_.name == projectName).flatMap(_.docs.find(_.path == docPath))
  }

  private def getInputServerAddress(): Option[ServerAddress] = {
    serverAddressTextField.text.split(":") match {
      case Array(ip, port) => Try(ServerAddress(ip, port.toInt)).toOption
      case _ => None
    }
  }


  private def clearAll(): Unit = {
    fileTree.clear()
    docEventList.clearItems()
    fileContentTextArea.clear()
    filePathLabel.clear()
  }

}

object MainDialog extends _MainDialog with VirtualDialog {
  val serverVersionLabel: RichLabel = new RichLabel(_serverVersionLabel)
  val filePathLabel: RichLabel = new RichLabel(_filePathLabel)
  val fileContentTextArea: RichTextArea = new RichTextArea(_fileContentTextArea)
  val serverAddressTextField: RichTextField = new RichTextField(_serverAddressTextField)
  val docEventList: RichList[DocEventItemData] = new RichList(_docEventList)
  val fileTree: RichTree = new RichTree(_fileTree)
  val connectButton: RichButton = new RichButton(_connectButton)
  val closeButton: RichButton = new RichButton(_closeButton)

  init()

  def main(args: Array[String]) {
    this.pack()
    this.setSize(800, 600)
    this.setVisible(true)
    System.exit(0)
  }

}
