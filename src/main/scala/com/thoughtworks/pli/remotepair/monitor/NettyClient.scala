package com.thoughtworks.pli.remotepair.monitor

import java.nio.charset.Charset

import com.thoughtworks.pli.intellij.remotepair.protocol.ParseEvent
import io.netty.bootstrap.Bootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel._
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.string.{StringEncoder, StringDecoder}
import io.netty.util.concurrent.GenericFutureListener

case class ServerAddress(ip: String, port: Int)

class NettyClient(serverAddress: ServerAddress)(parseEvent: ParseEvent) {

  def connect(handler: ChannelHandler): ChannelFuture = {
    val workerGroup = new NioEventLoopGroup(1)
    val bootstrap = new Bootstrap()
    bootstrap.group(workerGroup)
    bootstrap.channel(classOf[NioSocketChannel])
    bootstrap.option(ChannelOption.SO_KEEPALIVE.asInstanceOf[ChannelOption[Any]], true)
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS.asInstanceOf[ChannelOption[Any]], 5000)
    bootstrap.handler(new MyChannelInitializer(handler))
    val f = bootstrap.connect(serverAddress.ip, serverAddress.port).sync()
    f.channel().closeFuture().addListener(new GenericFutureListener[ChannelFuture] {
      override def operationComplete(f: ChannelFuture) {
        workerGroup.shutdownGracefully()
      }
    })
    f
  }

  class MyChannelInitializer(myHandler: ChannelHandler) extends ChannelInitializer[SocketChannel] {
    override def initChannel(ch: SocketChannel) {
      ch.pipeline().addLast(
        new LineBasedFrameDecoder(Int.MaxValue),
        new StringDecoder(Charset.forName("UTF-8")),
        new StringEncoder(Charset.forName("UTF-8")),
        new ChannelHandlerAdapter {
          override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
            println("netty read: " + msg)
            msg match {
              case line: String => ctx.fireChannelRead(parseEvent(line))
              case _ =>
            }
          }
          override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = {
            println("netty write: " + msg)
            super.write(ctx, msg, promise)
          }
        },
        myHandler
      )
    }
  }

}
