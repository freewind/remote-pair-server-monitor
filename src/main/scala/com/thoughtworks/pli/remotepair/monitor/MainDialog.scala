package com.thoughtworks.pli.remotepair.monitor

import SwingVirtualImplicits._
import io.netty.channel.{ChannelHandlerContext, ChannelHandlerAdapter}

import scala.util.Try

object MainDialog extends _MainDialog {

  private var nettyClient: Option[NettyClient] = None

  case class ServerAddress(ip: String, port: Int)
  private def connectToServer(serverAddress: ServerAddress): Unit = {
    if (nettyClient.isEmpty) nettyClient = Some(new NettyClient(serverAddress))
    nettyClient.foreach { client =>
      client.connect(new ChannelHandlerAdapter {

        override def channelActive(ctx: ChannelHandlerContext): Unit = {
          ctx.writeAndFlush(???)
        }
        override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
          ???
        }
      })
    }
  }

  private def getInputServerAddress(): Option[ServerAddress] = {
    serverAddressTextField.trimmedText.split(":") match {
      case Array(ip, port) => Try(ServerAddress(ip, port.toInt)).toOption
      case _ => None
    }
  }

  connectButton.onClick {
    getInputServerAddress() match {
      case Some(ServerAddress(ip, port)) => connectToServer(ip, port)
      case _ =>
    }
  }

  def main(args: Array[String]) {
    val dialog = new _MainDialog
    dialog.pack()
    dialog.setVisible(true)
    System.exit(0)
  }

}
