/*
 *  Copyright 2006 Simon Raess
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.sf.beep4j.internal.tcp;

import java.util.HashMap;
import java.util.Map;

import net.sf.beep4j.Message;
import net.sf.beep4j.ProtocolException;
import net.sf.beep4j.internal.stream.BeepStream;
import net.sf.beep4j.internal.stream.TransportMapping;
import net.sf.beep4j.internal.util.Assert;
import net.sf.beep4j.transport.Transport;

/**
 * The TCPMapping implements the mapping of BEEP onto TCP as specified
 * by RFC 3081.
 * 
 * @author Simon Raess
 */
public class TCPMapping implements TransportMapping, BeepStream, ChannelControllerFactory {

	private static final int DEFAULT_BUFFER_SIZE = 4096;
	
	private final Transport transport;
	
	private final ChannelControllerFactory factory;
	
	private final int bufferSize;
	
	private final Map<Integer, ChannelController> channels = 
			new HashMap<Integer, ChannelController>();
	
	/**
	 * Whether the transport has been closed.
	 */
	private boolean closed;
	
	public TCPMapping(Transport transport) {
		this(transport, null);
	}
	
	public TCPMapping(Transport transport, ChannelControllerFactory factory) {
		this(transport, factory, DEFAULT_BUFFER_SIZE);
	}
	
	public TCPMapping(Transport transport, ChannelControllerFactory factory, int bufferSize) {
		Assert.notNull("transport", transport);
		this.transport = transport;
		this.factory = factory != null ? factory : this;
		this.bufferSize = bufferSize;
	}
	
	/**
	 * Determines whether the underlying transport has been closed.
	 * 
	 * @return true iff the underlying transport has been closed
	 */
	public boolean isClosed() {
		return closed;
	}
	
	// --> start of SessionListener methods <--
	
	public synchronized void channelStarted(int channelNumber) {
		if (channels.containsKey(channelNumber)) {
			throw new IllegalArgumentException("there is already a channel for channel number: " 
					+ channelNumber);
		}
		ChannelController controller = factory.createChannelController(channelNumber, transport);
		channels.put(channelNumber, controller);
	}
	
	public synchronized void channelClosed(int channelNumber) {
		channels.remove(channelNumber);
	}
	
	// --> end of SessionListener methods <--
	
	/**
	 * Gets the {@link ChannelController} for the given <var>channel</var>.
	 * Throws a {@link ProtocolException} if there is no ChannelController
	 * for the given channel.
	 * 
	 * @param channel the channel number
	 * @return the ChannelController for the given channel
	 * @throws ProtocolException if the given channel is not open
	 */
	protected synchronized ChannelController getChannelController(int channel) {
		ChannelController controller = channels.get(new Integer(channel));
		if (controller == null) {
			throw new ProtocolException("unknown channel: " + channel);
		}
		return controller;
	}
	
	/**
	 * Gets the existing ChannelController or a special NullChannelController if
	 * no such channel exists. This method has to be used by {@link #frameReceived(int, long, int)}
	 * because that method might be called after {@link #channelClosed(int)} has
	 * been called for that channel. This can happen only if the close channel
	 * request is not accepted right away, because the local peer still awaits
	 * replies to messages it has sent. When it receives the last of those
	 * replies the {@link #channelClosed(int)} method is called on the
	 * same call stack.
	 * 
	 * <p>A NullChannelController is returned if the channel has been closed.
	 * It does not make sense to send a mapping frame in these cases. So
	 * a null object is a correct choice.</p>
	 * 
	 * @param channel
	 * @return a ChannelController for that channel
	 */
	protected synchronized ChannelController lenientGetChannelController(int channel) {
		ChannelController controller = channels.get(new Integer(channel));
		
		if (controller == null) {
			controller = ChannelController.NULL;
		}

		return controller;
	}

	
	// --> start of ChannelControllerFactory methods <--
	
	public DefaultChannelController createChannelController(int channelNumber, Transport transport) {
		return new DefaultChannelController(transport, channelNumber, bufferSize);
	}
	
	// --> end of ChannelControllerFactory methods <--
	
	
	// --> start of TransportMapping methods <--
	
	public void checkFrame(int channel, long seqno, int size) {
		getChannelController(channel).checkFrame(seqno, size);
	}
	
	public void frameReceived(int channel, long seqno, int size) {
		lenientGetChannelController(channel).frameReceived(seqno, size);
	}

	public void processMappingFrame(String[] tokens) {
		if (!SEQHeader.TYPE.equals(tokens[0])) {
			throw new ProtocolException("unsupported frame type: " + tokens[0]);
		}
		
		SEQHeader header = new SEQHeader(tokens);
		int channel = header.getChannel();
		long ackno = header.getAcknowledgeNumber();
		int size = header.getWindowSize();
			
		// adapt the local view of the other peers window			
		getChannelController(channel).updateSendWindow(ackno, size);
	}
	
	// --> end of TransportMapping methods <--
	
	
	// --> start of BeepStream methods <--
	
	public void sendANS(int channel, int messageNumber, int answerNumber, Message message) {
		getChannelController(channel).sendANS(messageNumber, answerNumber, message);
	}
	
	public void sendERR(int channel, int messageNumber, Message message) {
		getChannelController(channel).sendERR(messageNumber, message);
	}
	
	public void sendMSG(int channel, int messageNumber, Message message) {
		getChannelController(channel).sendMSG(messageNumber, message);		
	}
	
	public void sendNUL(int channel, int messageNumber) {
		getChannelController(channel).sendNUL(messageNumber);
	}
	
	public void sendRPY(int channel, int messageNumber, Message message) {
		getChannelController(channel).sendRPY(messageNumber, message);
	}
	
	public void closeTransport() {
		transport.closeTransport();
		closed = true;
	}
	
	// --> end of BeepStream methods <--

}
