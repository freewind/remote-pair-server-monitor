package com.thoughtworks.pli.remotepair.monitor

import _root_.rx.schedulers.SwingScheduler
import com.thoughtworks.pli.intellij.remotepair.protocol.{PairEvent, ParseEvent}
import com.thoughtworks.pli.remotepair.monitor.PairServerConnector.{Connected, ConnectorEvent, Disconnected}
import io.netty.channel.{ChannelFuture, ChannelHandlerAdapter, ChannelHandlerContext, ChannelPromise}
import rx.lang.scala.subjects.PublishSubject

object SwingSchedulerEx {
  val eventScheduler = rx.lang.scala.JavaConversions.javaSchedulerToScalaScheduler(SwingScheduler.getInstance)
}

object PairServerConnector {
  sealed trait ConnectorEvent
  case class Connected(connection: ChannelHandlerContext) extends ConnectorEvent
  case object Disconnected extends ConnectorEvent
}

class PairServerConnector {
  private val parseEvent = new ParseEvent
  private var nettyClient: Option[NettyClient] = None
  private var channelFuture: Option[ChannelFuture] = None

  val connectorEvents = PublishSubject[ConnectorEvent]()
  val receivedEvents = PublishSubject[PairEvent]

  def connect(serverAddress: ServerAddress): Unit = {
    if (nettyClient.isEmpty) nettyClient = Some(new NettyClient(serverAddress)(parseEvent))
    channelFuture = nettyClient.map { client =>
      client.connect(new ChannelHandlerAdapter {
        override def channelActive(ctx: ChannelHandlerContext): Unit = connectorEvents.onNext(Connected(ctx))
        override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = receivedEvents.onNext(msg.asInstanceOf[PairEvent])
        override def disconnect(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit = connectorEvents.onNext(Disconnected)
      })
    }
  }

  def disconnect(): Unit = {
    channelFuture.foreach(_.channel().close())
    clear()
  }

  private def clear(): Unit = {
    nettyClient = None
    channelFuture = None
  }

}
