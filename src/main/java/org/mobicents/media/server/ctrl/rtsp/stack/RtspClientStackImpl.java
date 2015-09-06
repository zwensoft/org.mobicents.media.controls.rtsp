/*
 * JBoss, Home of Professional Open Source
 * Copyright XXXX, Red Hat Middleware LLC, and individual contributors as indicated
 * by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.media.server.ctrl.rtsp.stack;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sdp.MediaDescription;

import org.apache.log4j.Logger;
import org.mobicents.media.server.ctrl.rtsp.rtp.RTPSession;
import org.mobicents.media.server.io.network.PortManager;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspRequestEncoder;
import io.netty.handler.codec.rtsp.RtspVersions;

/**
 * 
 * @author amit.bhayani
 * 
 */
public class RtspClientStackImpl implements RtspStack {

	private static Logger logger = Logger.getLogger(RtspClientStackImpl.class);

	private final PortManager portManager;
	private final String remoteHost;
	private final int remotePort;
	private final String uri;
	private final InetAddress inetAddress;
	private final  String url;
	
	private Channel channel = null;
	private Bootstrap bootstrap;
	private EventLoopGroup workerGroup = new NioEventLoopGroup();
	private RtspListener listener = null;
	
	private String session;
	private List<RTPSession> rtpSessions = new ArrayList<RTPSession>();

	public RtspClientStackImpl(PortManager portManager, String address, int port, String uri)
			throws UnknownHostException {
		String url = "rtsp://" + getAddress() + ":" + getPort() + uri;
		
		this.portManager = portManager;
		this.remoteHost = address;
		this.remotePort = port;
		this.uri = uri;
		this.url = url;
		this.inetAddress = InetAddress.getByName(this.remoteHost);

	}

	public String getAddress() {
		return this.remoteHost;
	}

	public int getPort() {
		return this.remotePort;
	}

	public void start() throws IOException {

		Bootstrap b = new Bootstrap();
		b.group(workerGroup);
		b.channel(NioSocketChannel.class);
		b.option(ChannelOption.SO_KEEPALIVE, true);
		b.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				String url = "rtsp://" + getAddress() + ":" + getPort() + uri;
				
				// 客户端接收到的是httpResponse响应，所以要使用HttpResponseDecoder进行解码
				ch.pipeline().addLast(new RtspResponseDecoder());
				// 客户端发送的是httprequest，所以要使用HttpRequestEncoder进行编码
				ch.pipeline().addLast(new RtspRequestEncoder());
				//ch.pipeline().addLast(new HttpObjectAggregator(1024 * 64));
				ch.pipeline().addLast(new RtspResponseHandler(RtspClientStackImpl.this, url));
			}
		});

		// Start the client.
		ChannelFuture f = b.connect(getAddress(), remotePort);
		try {
			f.sync();
			channel = f.channel();
			
			InetSocketAddress bindAddress = new InetSocketAddress(this.inetAddress,
					this.remotePort);

			logger.info("Mobicents RTSP Client started and bound to "
					+ bindAddress.toString());

		} catch (InterruptedException e) {
			throw new IOException(f.cause());
		}
	}

	public RTPSession createRtpSession(MediaDescription md) {
		
		RTPSession rtp = new RTPSession(null, portManager.next(), portManager.peek());
		rtp.setRtpInterleaved(rtpSessions.size() * 2 + 0);
		rtp.setRtcpInterleaved(rtpSessions.size() * 2 + 1);
		rtp.setMediaDescription(md);
		rtp.setServerHost(remoteHost);
		rtpSessions.add(rtp);
		return rtp;
	}
	
	public RTPSession getLastRtpSession() {
		return rtpSessions.isEmpty() ? null : rtpSessions.get(rtpSessions.size() - 1);
	}
	
	protected void processRtspResponse(HttpResponse rtspResponse) {
		synchronized (this.listener) {
			listener.onRtspResponse(rtspResponse);
		}
	}

	protected void processRtspRequest(HttpRequest rtspRequest, Channel channel) {
		synchronized (this.listener) {
			listener.onRtspRequest(rtspRequest, channel);
		}
	}

	public void stop() {
		ChannelFuture cf = channel.closeFuture();
		cf.addListener(new ClientChannelFutureListener());

		channel.close();
		cf.awaitUninterruptibly();
		
		workerGroup.shutdownGracefully();

	}

	public void setRtspListener(RtspListener listener) {
		this.listener = listener;

	}

	private class ClientChannelFutureListener implements ChannelFutureListener {

		public void operationComplete(ChannelFuture arg0) throws Exception {
			logger.info("Mobicents RTSP Client Stop complete");
		}

	}

	public void sendRquest(HttpRequest rtspRequest, String remoteHost, int remotePort) {

		ChannelFuture future = null;
		if (channel == null || (channel != null && !channel.isOpen())) {
			// Start the connection attempt.
			future = bootstrap.connect(new InetSocketAddress(remoteHost, remotePort));
		}

		// Wait until the connection attempt succeeds or fails.
		channel = future.awaitUninterruptibly().channel();
		if (!future.isSuccess()) {
			future.cause().printStackTrace();
			// bootstrap.releaseExternalResources();
			return;
		}

		channel.writeAndFlush(rtspRequest);
	}

	public String getRemoteHost() {
		return remoteHost;
	}
	
	public int getRemotePort() {
		return remotePort;
	}

	public void setSession(String sessionId) {
		this.session = sessionId;
	}
	
	public String getSession() {
		return session;
	}
	
	public String getUrl() {
		return url;
	}

}
