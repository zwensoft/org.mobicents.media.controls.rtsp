package org.mobicents.media.server.ctrl.rtsp;

import java.io.IOException;

import org.mobicents.media.server.ctrl.rtsp.stack.RtspClientStackImpl;
import org.mobicents.media.server.io.network.PortManager;

import junit.framework.TestCase;

public class TestRtspClient extends TestCase {
	private RtspClientStackImpl stack;
	
	@Override
	protected void setUp() throws Exception {
		// String url = "";
		//  private RtspSession client = new RtspSession("172.16.176.165", 554, "/caozhen", "admin", "12345");
		PortManager portManager = new PortManager();
		String address = "172.16.176.165";
		int port = 554;
		String uri = "/caozhen";

		stack = new RtspClientStackImpl(portManager, address, port, uri);
	}
	
	public void testStart() throws IOException, InterruptedException {
		stack.start();
		
		Thread.sleep(30 * 1000);
	}
	
	@Override
	protected void tearDown() throws Exception {
		stack.stop();
	}
	
}
