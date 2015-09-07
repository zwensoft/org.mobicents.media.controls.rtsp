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
import java.net.UnknownHostException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author amit.bhayani
 * 
 */
public class RtspServerStackImpl implements RtspStack {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RtspServerStackImpl.class);

	private static final int BIZGROUPSIZE = Runtime.getRuntime().availableProcessors() * 2;
	private static final int BIZTHREADSIZE = 4;
	private static final EventLoopGroup bossGroup = new NioEventLoopGroup(BIZGROUPSIZE);
	private static final EventLoopGroup workerGroup = new NioEventLoopGroup(BIZTHREADSIZE);

	private final String address;
	private final int port;
	private final InetAddress inetAddress;
	private Channel channel = null;
	private ServerBootstrap server = null;

	private RtspListener listener = null;

	public RtspServerStackImpl(String address, int port) throws UnknownHostException {
		this.address = address;
		this.port = port;
		inetAddress = InetAddress.getByName(this.address);
	}

	public String getHost() {
		return this.address;
	}

	public int getPort() {
		return this.port;
	}

	public void start() throws IOException {
		try {
			server = new ServerBootstrap();
			server.group(bossGroup, workerGroup);
			server.channel(NioServerSocketChannel.class);
			server.childHandler(new RtspServerInitializer(this).get());
			channel = server.bind(address, port).sync().channel();

			logger.info("Mobicents RTSP Server started and bound to " + address);
		} catch (InterruptedException ex) {
			throw new IOException("Fail bind" + address + ":" + port, ex);
		}
	}

	public void stop() {
		if (null != channel) {
			channel.close();
			channel = null;
		}

		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
	}

	public void setRtspListener(RtspListener listener) {
		this.listener = listener;

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

	private class ServerChannelFutureListener implements ChannelFutureListener {

		public void operationComplete(ChannelFuture arg0) throws Exception {
			logger.info("Mobicents RTSP Server Stop complete");
		}

	}

	public void sendRquest(HttpRequest rtspRequest, String host, int port) {
		throw new UnsupportedOperationException("Not Supported yet");
	}
}

class RtspServerBossThreadFactory implements ThreadFactory {

	public static final AtomicLong sequence = new AtomicLong(0);
	private ThreadGroup factoryThreadGroup = new ThreadGroup(
			"RtspServerBossThreadGroup[" + sequence.incrementAndGet() + "]");

	public Thread newThread(Runnable r) {
		Thread t = new Thread(this.factoryThreadGroup, r);
		t.setPriority(Thread.NORM_PRIORITY);
		return t;
	}
}

class RtspServerWorkerThreadFactory implements ThreadFactory {

	public static final AtomicLong sequence = new AtomicLong(0);
	private ThreadGroup factoryThreadGroup = new ThreadGroup(
			"RtspServerWorkerThreadGroup[" + sequence.incrementAndGet() + "]");

	public Thread newThread(Runnable r) {
		Thread t = new Thread(this.factoryThreadGroup, r);
		t.setPriority(Thread.NORM_PRIORITY);
		return t;
	}
}
