package org.mobicents.media.server.ctrl.rtsp.stack;

import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

import org.mobicents.media.server.ctrl.rtsp.rtp.RTPSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;

public class RtspResponseHandler extends ChannelInboundHandlerAdapter {
	private static Logger logger = LoggerFactory.getLogger(RtspResponseHandler.class);
	private static enum RTSP {
		UNINIT, OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN
	};

	final private RtspClientStackImpl rtspStack;
	private ChannelHandlerContext ctx;
	private RTSP state = RTSP.UNINIT;
	
	private SessionDescription sd;
	private int mediaIndex = 0;
	private AtomicInteger cseq = new AtomicInteger(1);

	public RtspResponseHandler(RtspClientStackImpl client, String url) {
		this.rtspStack = client;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		this.ctx = ctx;

		setState(RTSP.OPTIONS);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		this.ctx = null;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		if (msg instanceof DefaultFullHttpResponse) {
			messageReceive(ctx, (DefaultFullHttpResponse)msg);
		} else {
			logger.warn("error msg: {}", msg);;
		}

		/** send next */
		super.channelRead(ctx, msg);
	}

	private void messageReceive(ChannelHandlerContext ctx, DefaultFullHttpResponse response) {
		/** send next cmd **/
		switch (state) {
		case PLAY:
			String rtpInfo = response.headers().get("RTP-Info");
			
			break;
		case SETUP:
			// session
			String sessionId = response.headers().get("Session");
			rtspStack.setSession(sessionId);
			
			// transport
			String transport = response.headers().get("Transport");
			RTPSession rtp = rtspStack.getLastRtpSession();
			Matcher matcher = Pattern.compile("([^\\s=;]+)=(([^-;]+)(-([^;]+))?)")
					.matcher(transport);
			while (matcher.find()) {
				String key = matcher.group(1).toLowerCase();
				if ("server_port".equals(key)) {
					rtp.setServerRtpPort(Integer.valueOf(matcher.group(3)));
					rtp.setServerRtcpPort(Integer.valueOf(matcher.group(5)));
				} else if ("ssrc".equals(key)) {
					rtp.setSsrc(Long.parseLong(matcher.group(2).trim(), 16));
				} else if ("interleaved".equals(key)) {
					rtp.setRtpInterleaved(Integer.valueOf(matcher.group(3)));
					rtp.setRtcpInterleaved(Integer.valueOf(matcher.group(5)));
				} else {
					logger.warn("ignored [{}={}]", key, matcher.group(2));
				}
			}

			rtp.play();
			
			// next action
			boolean finish = setup(sd, mediaIndex ++);
			if (finish) {
				sendPlay();
				setState(RTSP.PLAY);
			}
			
			break;
		case DESCRIBE:
			String sdp = response.content().toString(Charset.forName("UTF8"));
			SessionDescriptionImpl sd = new SessionDescriptionImpl();
			StringTokenizer tokenizer = new StringTokenizer(sdp);
			while (tokenizer.hasMoreChars()) {
				String line = tokenizer.nextToken();

				try {
					SDPParser paser = ParserFactory.createParser(line);
					if (null != paser) {
						SDPField obj = paser.parse();
						sd.addField(obj);
					}
				} catch (ParseException e) {
					logger.warn("fail parse [{}]", line, e);
				}
			}
			this.sd = sd;
			
			mediaIndex = 0;
			setup(sd, mediaIndex ++);
			break;
		case OPTIONS:
			sendDescribe();
			setState(RTSP.DESCRIBE);
			break;
		default:
			logger.warn("I dont't Known What to do with {}", response);
			break;
		}
	}

	public boolean setup(SessionDescription sd, int mediaIndex) {
		boolean finish = false;
		try {
			Vector<MediaDescription> mds = sd.getMediaDescriptions(true);
			
			if (mds.size() <= mediaIndex) {
				finish = true;
			} else {
				MediaDescription md = mds.get(mediaIndex);
				
				RTPSession rtp = rtspStack.createRtpSession(md);
				
				
				sendTCPSetup(md.getAttribute("control"), rtp.getRtpInterleaved(), rtp.getRtcpInterleaved());
				
				
				setState(RTSP.SETUP);
			}
			
		} catch (SdpException e) {
			throw new IllegalArgumentException(e);
		}
		
		return finish;
		
	}
	
	public void tearDown() {
		if (null != ctx) {
			sendTeardown();
			setState(RTSP.TEARDOWN);
		}
	}

	private void setState(RTSP newState) {
		if (newState.ordinal() < state.ordinal()) {
			throw new IllegalStateException("can't change  state from " + this.state + " to " + newState);
		}

		this.state = newState;
	}

	

	public void sendOptions()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.OPTIONS, rtspStack.getUrl());
		addCSeq(req);
		
		rtspStack.sendRquest(req, rtspStack.getRemoteHost(), rtspStack.getRemotePort());
	}
	
	public void sendDescribe()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, rtspStack.getUrl());
		addCSeq(req);
		req.headers().add("Accept", "application/sdp");

		rtspStack.sendRquest(req, rtspStack.getRemoteHost(), rtspStack.getRemotePort());
	}

	public void sendUDPSetup(String track, int rtp, int rtcp)  {
		String trackUrl = track;
		if (track.startsWith("rtsp://")) {
			trackUrl = track;
		} else {
			throw new IllegalArgumentException("unsupported track url:" + track);
		}
		
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, trackUrl);
		addCSeq(req);
		req.headers().add("Transport", "RTP/AVP;unicast;client_port="+rtp + "-" + rtcp);

		rtspStack.sendRquest(req, rtspStack.getRemoteHost(), rtspStack.getRemotePort());
	}
	
	/**
	 * Transport: RTP/AVP/TCP;interleaved=${rtp}-${rtcp}
	 * 
	 * @param track
	 * @param rtp
	 * @param rtcp
	 */
	public void sendTCPSetup(String track, int rtp, int rtcp)  {
		String trackUrl = track;
		if (track.startsWith("rtsp://")) {
			trackUrl = track;
		} else {
			throw new IllegalArgumentException("unsupported track url:" + track);
		}
		
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, trackUrl);
		addCSeq(req);
		req.headers().add("Transport", "RTP/AVP/TCP;interleaved="+rtp + "-" + rtcp);

		rtspStack.sendRquest(req, rtspStack.getRemoteHost(), rtspStack.getRemotePort());
	}

	public void sendPlay()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.PLAY, rtspStack.getUrl());
		addCSeq(req);
		req.headers().add("Session", rtspStack.getSession());
		req.headers().add("Range", "npt=0.000");

		rtspStack.sendRquest(req, rtspStack.getRemoteHost(), rtspStack.getRemotePort());
	}

	public void sendTeardown()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.TEARDOWN, rtspStack.getUrl());
		addCSeq(req);
		
		String session = rtspStack.getSession();
		if (null != session) {
			req.headers().add("Session", session);
		}

		rtspStack.sendRquest(req, rtspStack.getRemoteHost(), rtspStack.getRemotePort());
	}
	
	private void addCSeq(DefaultFullHttpRequest req) {
		req.headers().add("CSeq", cseq.toString());
		cseq.incrementAndGet();
	}
}