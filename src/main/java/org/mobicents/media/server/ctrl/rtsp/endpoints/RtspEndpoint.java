package org.mobicents.media.server.ctrl.rtsp.endpoints;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

import org.mobicents.media.core.endpoints.BaseEndpointImpl;
import org.mobicents.media.server.ctrl.rtsp.stack.RtspClientStackImpl;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.spi.Connection;
import org.mobicents.media.server.spi.ConnectionMode;
import org.mobicents.media.server.spi.ConnectionType;
import org.mobicents.media.server.spi.ResourceUnavailableException;
import org.mobicents.media.server.spi.listener.TooManyListenersException;

public class RtspEndpoint extends BaseEndpointImpl {
	private static AtomicLong CONNECTION_ID = new AtomicLong(1000000);
	
	private UdpManager udpManager;
	private RtspClientStackImpl stack;
	
	public RtspEndpoint(String localName, String url, UdpManager udpManager) throws UnknownHostException {
		super(localName);
		this.udpManager = udpManager;
		
		stack = new RtspClientStackImpl(udpManager, url);
	}

	@Override
	public void modeUpdated(ConnectionMode oldMode, ConnectionMode newMode) {
		
	}
	
	@Override
	public void start() throws ResourceUnavailableException {
		try {
			stack.start();
		} catch (IOException e) {
			stack.stop();
			throw new ResourceUnavailableException(stack.getUrl());
		}
	}

	@Override
	public Connection createConnection(ConnectionType type, Boolean isLocal) throws ResourceUnavailableException {
		int id = (int)(CONNECTION_ID.getAndIncrement() & 0x0FFFFFFF);
		RtspConnection rtsp = new RtspConnection(id, udpManager, getScheduler());

		try {
			stack.addListener(rtsp);
		} catch (TooManyListenersException ex) {
			throw new ResourceUnavailableException(ex);
		}

		rtsp.setSessionDescription(stack.getSessionDescription());
		return rtsp;
	}

}
