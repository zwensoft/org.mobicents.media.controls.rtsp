package org.mobicents.media.server.ctrl.rtsp.endpoints;

import java.io.IOException;

import javax.sdp.SessionDescription;

import org.mobicents.media.core.connections.BaseConnection;
import org.mobicents.media.server.component.audio.AudioComponent;
import org.mobicents.media.server.component.oob.OOBComponent;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.Connection;
import org.mobicents.media.server.spi.ConnectionFailureListener;
import org.mobicents.media.server.spi.listener.Listener;
import org.mobicents.media.server.utils.Text;

public class RtspConnection extends BaseConnection implements Listener<RtspPacketEvent> {
	protected UdpManager udpManager;
	private String descriptor;
	
	
	public RtspConnection(int id, UdpManager manager, Scheduler scheduler) {
		super(id, scheduler);
		
		
		this.udpManager = manager;
	}

	@Override
	public String getDescriptor() {
		return descriptor;
	}
	
	public void setSessionDescription(SessionDescription  sd) {
		this.descriptor = sd.toString();
	}
	
	@Override
	public void generateOffer() throws IOException {
		
	}

	@Override
	public long getPacketsReceived() {
		return 0;
	}

	@Override
	public long getBytesReceived() {
		return 0;
	}

	@Override
	public long getPacketsTransmitted() {
		return 0;
	}

	@Override
	public long getBytesTransmitted() {
		return 0;
	}

	@Override
	public double getJitter() {
		return 0;
	}

	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public AudioComponent getAudioComponent() {
		return null;
	}

	@Override
	public OOBComponent getOOBComponent() {
		return null;
	}

	@Override
	public void setConnectionFailureListener(ConnectionFailureListener connectionFailureListener) {
		
	}

	@Override
	protected void onCreated() throws Exception {
		
	}

	@Override
	protected void onOpened() throws Exception {
		
	}

	@Override
	protected void onClosed() {
		
	}

	@Override
	protected void onFailed() {
		
	}

	@Override
	public void setOtherParty(Connection other) throws IOException {
		
	}

	@Override
	public void setOtherParty(byte[] descriptor) throws IOException {
		
	}

	@Override
	public void setOtherParty(Text descriptor) throws IOException {
		
	}

	@Override
	public void process(RtspPacketEvent event) {
		
	}

}
