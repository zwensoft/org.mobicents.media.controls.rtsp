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
package org.mobicents.media.server.ctrl.rtsp;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

import javax.sdp.MediaDescription;

import org.apache.log4j.Logger;
import org.mobicents.media.server.spi.Connection;
import org.mobicents.media.server.spi.ConnectionType;
import org.mobicents.media.server.spi.Endpoint;
import org.mobicents.media.server.spi.ResourceUnavailableException;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

/**
 * 
 * @author amit bhayani
 * 
 */
public class DescribeAction implements Callable<FullHttpResponse> {

	private static Logger logger = Logger.getLogger(DescribeAction.class);
	private RtspController rtspController = null;
	private HttpRequest request = null;

	private static final String DATE_PATTERN = "EEE, d MMM yyyy HH:mm:ss z";

	public DescribeAction(RtspController rtspController, HttpRequest request) {
		this.rtspController = rtspController;
		this.request = request;
	}

	public FullHttpResponse call() throws Exception {
		FullHttpResponse response = null;

		URI objUri = new URI(this.request.getUri());

		String srcUrl = objUri.getPath();
		MediaDescription description = rtspController.describe(srcUrl);
		
		/** not found */
		if (description == null) {
			logger.warn("No EndpointName passed in request " + srcUrl);
			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.NOT_FOUND);
			response.headers().set(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().set(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
			return response;
		}
		
		/***/
		String sdp = null;
		try {
			
		} catch (ResourceUnavailableException e) {
			logger.warn("There is no free endpoint: " + srcUrl);
			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.SERVICE_UNAVAILABLE);
			response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
			return response;
		}


		
		//we hard-code here
//		sdp = "v=0\n" +
//		"o=- 3776780 3776780 IN IP4 127.0.0.1\n" +
//		"s=Mobicents Media Server\n" +
//		"c=IN IP4 127.0.0.1\n" +
//		"t=0 0\n" +
//		"m=audio 0 RTP/AVP 0 2 3 97 8\n" +
//		"b=AS:20\n"+
//		"a=rtpmap:0 pcmu/8000\n" +		
//		"a=rtpmap:2 g729/8000\n" +
//		"a=rtpmap:3 gsm/8000\n" +
//		"a=rtpmap:97 speex/8000\n" +
//		"a=rtpmap:8 pcma/8000\n" +
//		"a=control:audio\n";
		
//		sdp = "v=0\n" +
//		"o=MobicentsMediaServer 6732605 6732605 IN IP4 127.0.0.1\n"+
//		"s=session\n"+
//		"c=IN IP4 127.0.0.1\n"+
//		"t=0 0\n"+
//		"m=audio 0 RTP/AVP 97\n"+
//		"b=AS:20\n"+
//		"a=rtpmap:97 mpeg4-generic/8000/2\n"+
//		"a=control:trackID=4\n"+
//		"a=fmtp:97 profile-level-id=15;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1590\n"+
//		"a=mpeg4-esid:101\n"+
//		"m=video 0 RTP/AVP 96\n"+
//		"b=AS:76\n"+
//		"a=rtpmap:96 MP4V-ES/90000\n"+
//		"a=control:trackID=3\n"+
//		"a=cliprect:0,0,242,192\n"+
//		"a=framesize:96 192-242\n"+
//		"a=fmtp:96 profile-level-id=1;config=000001B0F3000001B50EE040C0CF0000010000000120008440FA283020F2A21F\n"+
//		"a=mpeg4-esid:201\n";

		
		
		
		System.out.println("sdp = "+ sdp);
		// TODO get Description from Endpoint
		// sdp = endpoint.describe(reqParser.getMediaType(), reqParser.getMediaFile());

		// TODO : Shoud endpoint.describe() return Description object which has all parameters like lastModified, sdp
		// etc


		response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK, Unpooled.copiedBuffer(sdp.getBytes("UTF-8")));
		response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
		response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
		response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/sdp");
		response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(sdp.length()));
		// TODO CACHE_CONTROL must come from settings. Let user decide how they want CACHE_CONTROL
		response.headers().add(HttpHeaders.Names.CACHE_CONTROL, HttpHeaders.Values.MUST_REVALIDATE);

		return response;
	}
}
