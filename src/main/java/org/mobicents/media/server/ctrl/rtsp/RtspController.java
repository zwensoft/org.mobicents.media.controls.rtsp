package org.mobicents.media.server.ctrl.rtsp;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;

import org.mobicents.media.core.naming.NamingService;
import org.mobicents.media.core.naming.UnknownEndpointException;
import org.mobicents.media.server.ctrl.rtsp.config.ServerConfig;
import org.mobicents.media.server.ctrl.rtsp.endpoints.RtspConnection;
import org.mobicents.media.server.ctrl.rtsp.endpoints.RtspEndpoint;
import org.mobicents.media.server.ctrl.rtsp.session.BaseSession;
import org.mobicents.media.server.ctrl.rtsp.session.BasicSessionStore;
import org.mobicents.media.server.ctrl.rtsp.session.DefaultSessionAccessor;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSession;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSessionAccessor;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSessionKeyFactory;
import org.mobicents.media.server.ctrl.rtsp.session.SimpleRandomKeyFactory;
import org.mobicents.media.server.ctrl.rtsp.stack.RtspListener;
import org.mobicents.media.server.ctrl.rtsp.stack.RtspServerStackImpl;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.scheduler.Task;
import org.mobicents.media.server.spi.Endpoint;
import org.mobicents.media.server.spi.ResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

public class RtspController implements RtspListener {
	private static Logger logger = LoggerFactory.getLogger(RtspController.class);
	public static final String SERVER = "RtspServer";
	public static final String REQUIRE_VALUE_NGOD_R2 = "com.comcast.ngod.r2";
	public static final String REQUIRE_VALUE_NGOD_C1 = "com.comcast.ngod.c1";
	private static final String DATE_PATTERN = "EEE, d MMM yyyy HH:mm:ss z";

	public static final RtspSessionAccessor sessionAccessor = new DefaultSessionAccessor();
	public static final RtspSessionKeyFactory keyFactory = new SimpleRandomKeyFactory();

	private String ip;
	private int port;
	private ServerConfig serverConfig;
	private RtspServerStackImpl server = null;
	private NamingService namingService = new NamingService();
	
	private Scheduler scheduler;
	private UdpManager udpManager;
	private BasicSessionStore sessionStore;

	public void start() throws Exception {
		this.server = new RtspServerStackImpl(ip, port);
		this.server.setRtspListener(this);
		this.server.start();
		logger.debug("Started Rtsp Server. ");
	}

	public void stop() {
		this.server.stop();
	}

	public void onRtspRequest(HttpRequest request, Channel channel) {
		logger.info("Receive request " + request);
		Callable<HttpResponse> action = null;
		HttpResponse response = null;
		try {

			if (request.getMethod().equals(RtspMethods.OPTIONS)) {
				action = new OptionsAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.DESCRIBE)) {
				action = new DescribeAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.SETUP)) {
				InetSocketAddress inetSocketAddress = (InetSocketAddress) channel.remoteAddress();
				String remoteIp = inetSocketAddress.getAddress().getHostAddress();
				action = new SetupAction(this, request, remoteIp);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.PLAY)) {
				action = new PlayAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.TEARDOWN)) {
				action = new TeardownAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(HttpMethod.GET)) {

				String date = new SimpleDateFormat(DATE_PATTERN).format(new Date());

				response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK);
				response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
				response.headers().add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
				response.headers().add(HttpHeaders.Names.DATE, date);
				response.headers().add(HttpHeaders.Names.CACHE_CONTROL, HttpHeaders.Values.NO_STORE);
				response.headers().add(HttpHeaders.Names.PRAGMA, HttpHeaders.Values.NO_CACHE);
				response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/x-rtsp-tunnelled");

			} else if (request.getMethod().equals(HttpMethod.POST)) {
				// http://developer.apple.com/quicktime/icefloe/dispatch028.html
				// The POST request is never replied to by the server.
				logger.info("POST Response = " + response);
				// TODO : Map this request to GET

				return;

				// response = new DefaultHttpResponse(HttpVersion.HTTP_1_0,
				// HttpResponseStatus.NOT_IMPLEMENTED);
			} else {
				response = new DefaultHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.METHOD_NOT_ALLOWED);
				response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
				response.headers().add(RtspHeaders.Names.CSEQ, request.headers().get(RtspHeaders.Names.CSEQ));
				response.headers().add(RtspHeaders.Names.ALLOW, OptionsAction.OPTIONS);
			}

		} catch (Exception e) {
			logger.error("Unexpected error during processing,Caused by ", e);

			response = new DefaultHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.INTERNAL_SERVER_ERROR);
			response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ, request.headers().get(RtspHeaders.Names.CSEQ));
		}

		logger.info("Sending Response " + response.toString() + " For Request " + request.toString());
		channel.writeAndFlush(response);
	}

	@Override
	public void onRtspResponse(HttpResponse response) {

	}

	/*-----------Setter And Getter --------------*/

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public ServerConfig getServerConfig() {
		return serverConfig;
	}

	public void setServerConfig(ServerConfig serverConfig) {
		this.serverConfig = serverConfig;
	}

	public Endpoint lookup(String name) throws ResourceUnavailableException, UnknownEndpointException {
		Endpoint endpoint = namingService.lookup(name, false);
		if (null == endpoint) {
			try {
				endpoint = new RtspEndpoint(name, name, udpManager);
				endpoint.start();
				
				namingService.register(endpoint);
				Endpoint stored = namingService.lookup(name, false);
				if (stored != endpoint) { // open two same endpoint, close one
					endpoint.stop();
					endpoint = stored;
				}
			} catch (UnknownHostException e) {
				throw new ResourceUnavailableException("fail open ", e);
			}
		}

		return endpoint;
	}
	

	public Set<String> getEndpoints() {
		return null;
	}

	public RtspSession getSession(String sessionID, boolean create) {
		return sessionStore.newSession(sessionID, create);
	}
}
