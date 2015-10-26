package com.thoughtworks.pli.remotepair.monitor

import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

import com.thoughtworks.pli.intellij.remotepair.protocol._
import com.thoughtworks.pli.intellij.remotepair.utils.ContentDiff
import com.thoughtworks.pli.remotepair.monitor.SwingVirtualImplicits._
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
  @Lenses("_") case class Doc(path: String, baseVersion: Int, baseContent: Content, changes: List[Change] = Nil)
  @Lenses("_") case class Project(name: String, docs: List[Doc] = Nil)
  @Lenses("_") case class Projects(projects: List[Project] = Nil)

  import Project._
  import Projects._


  private var projects: Projects = Projects()

  private def handleChangeContentConfirmation(projectName: String, event: ChangeContentConfirmation): Unit = {
    val change = Change(event.newVersion, event.diffs.toList)
    def addChange(doc: Doc): Doc = if (doc.path == event.path) doc.copy(changes = change :: doc.changes) else doc
    projects = (_projects composeTraversal each composeLens _docs composeTraversal each).modify(addChange)(projects)
  }

  private def handleCreateDocumentConfirmation(projectName: String, event: CreateDocumentConfirmation) = {
    val doc = Doc(event.path, baseVersion = event.version, baseContent = event.content)
    def addDoc(docs: List[Doc]): List[Doc] = (doc :: docs).sortBy(_.path)
    projects = (_projects composeTraversal each composeLens _docs).modify(addDoc)(projects)
    createTree()
  }

  private def handleServerStatusResponse(event: ServerStatusResponse) = {
    projects = Projects(event.projects.map(info => Project(info.name)).toList)
    createTree()
  }

  private def createTree(): Unit = {
    val root = new DefaultMutableTreeNode("projects")
    projects.projects.foreach(p => {
      val pNode = new DefaultMutableTreeNode(p.name)
      p.docs.foreach(d => pNode.add(new DefaultMutableTreeNode(d.path)))
      root.add(pNode)
    })
    fileTree.setModel(new DefaultTreeModel(root))
    fileTree.updateUI()
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
