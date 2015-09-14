package org.mobicents.media.server.ctrl.rtsp.stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.CharsetUtil;

public class RtspRequestHandler extends ChannelInboundHandlerAdapter {
  private static Logger logger = LoggerFactory.getLogger(RtspRequestHandler.class);
  private final RtspServerStackImpl rtspServerStackImpl;

  protected RtspRequestHandler(RtspServerStackImpl rtspServerStackImpl) {
    this.rtspServerStackImpl = rtspServerStackImpl;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof FullHttpRequest) {
    	FullHttpRequest request = (FullHttpRequest) msg;
      logger.debug("client request ========>\n{}\n\n{}", request.toString(), request.content()
          .toString(CharsetUtil.UTF_8));
      
     
      rtspServerStackImpl.processRtspRequest(request, ctx);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    logger.error(cause.getMessage(), cause);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    logger.debug("channelActive...............");
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    logger.debug("channelInactive...............");
  }
}
