package org.mobicents.media.server.ctrl.rtsp;

import junit.framework.TestCase;

public class TestRtspController extends TestCase {
	private Object lock = new Object();
	private RtspController controller;
	
	@Override
	protected void setUp() throws Exception {
		controller = new RtspController();
		controller.setIp("172.16.160.149");
		controller.setPort(554);
		
		
	}
	
	public void testServer() throws Exception {
		controller.start();
		
		synchronized (lock) {
			lock.wait();
		}
	}
}
