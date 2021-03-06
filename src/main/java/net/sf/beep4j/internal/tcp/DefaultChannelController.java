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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedList;

import net.sf.beep4j.Message;
import net.sf.beep4j.ProtocolException;
import net.sf.beep4j.internal.stream.DataHeader;
import net.sf.beep4j.internal.stream.Frame;
import net.sf.beep4j.internal.stream.MessageType;
import net.sf.beep4j.internal.stream.DataHeader.ANSHeader;
import net.sf.beep4j.internal.util.Assert;
import net.sf.beep4j.transport.Transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the {@link ChannelController} interface for use
 * by the TCP mapping implementation.
 * 
 * @author Simon Raess
 */
final class DefaultChannelController implements ChannelController {
	
	private static final Logger LOG = LoggerFactory.getLogger(ChannelController.class);
	
	private static final Charset ASCII_CHARSET = Charset.forName("US-ASCII");

	private static final int MINIMUM_FRAME_SIZE = 1;
	
	private final int channel;
	
	private final SlidingWindow window;
	
	private final SlidingWindow senderWindow;
	
	private final LinkedList<Frame> frames = new LinkedList<Frame>();
	
	private final Transport transport;
	
	private long seqno;
	
	public DefaultChannelController(Transport transport, int channel, int window) {
		Assert.notNull("transport", transport);
		this.transport = transport;
		this.channel = channel;
		this.senderWindow = new SlidingWindow(window);
		this.window = new SlidingWindow(window);
	}
	
	public synchronized void updateSendWindow(long ackno, int size) {
		LOG.debug("update send window: ackno=" + ackno + ",window=" + size);
		senderWindow.slide(ackno, size);
		sendFrames(transport);
	}
	
	public synchronized void sendANS(int messageNumber, int answerNumber, Message message) {
		LOG.debug("sendANS to message " + messageNumber + " with answer number "
				+ answerNumber + " on channel " + channel);
		ByteBuffer buffer = message.asByteBuffer();
		DataHeader header = new ANSHeader(
				channel, messageNumber, false, 
				seqno, 
				buffer.remaining(), answerNumber);

		seqno += buffer.remaining();
		
		Frame frame = new Frame(header, buffer);
		enqueueFrame(frame);
		sendFrames(transport);
	}
	
	public synchronized void sendERR(int messageNumber, Message message) {
		LOG.debug("sendERR to message " + messageNumber + " on channel " + channel);
		ByteBuffer buffer = message.asByteBuffer();
		DataHeader header = new DataHeader(
				MessageType.ERR,
				channel, messageNumber, false, 
				seqno, buffer.remaining());
		
		seqno += buffer.remaining();
		
		Frame frame = new Frame(header, buffer);
		enqueueFrame(frame);
		sendFrames(transport);
	}
	
	public synchronized void sendMSG(int messageNumber, Message message) {
		LOG.debug("sendMSG with message number " + messageNumber + " on channel " + channel);
		ByteBuffer buffer = message.asByteBuffer();
		DataHeader header = new DataHeader(
				MessageType.MSG,
				channel, messageNumber, false, 
				seqno, buffer.remaining());
		
		seqno += buffer.remaining();
		
		Frame frame = new Frame(header, buffer);
		enqueueFrame(frame);
		sendFrames(transport);
	}
	
	public synchronized void sendNUL(int messageNumber) {
		LOG.debug("sendNUL to message " + messageNumber + " on channel " + channel);
		DataHeader header = new DataHeader(
				MessageType.NUL,
				channel, messageNumber, false, 
				seqno, 0);
		
		Frame frame = new Frame(header, ByteBuffer.allocate(0));
		enqueueFrame(frame);
		sendFrames(transport);
	}
	
	public synchronized void sendRPY(int messageNumber, Message message) {
		LOG.debug("sendRPY to message " + messageNumber + " on channel " + channel);
		ByteBuffer buffer = message.asByteBuffer();
		DataHeader header = new DataHeader(
				MessageType.RPY,
				channel, messageNumber, false, 
				seqno, buffer.remaining());
		
		seqno += buffer.remaining();
		
		Frame frame = new Frame(header, buffer);
		enqueueFrame(frame);
		int count = sendFrames(transport);
		LOG.debug("sendRPY caused " + count + " frames to be sent");
	}
	
	long id;
	
	public synchronized void checkFrame(long seqno, int payloadSize) {
		if (seqno != window.getPosition()) {
			throw new ProtocolException("sequence number " + seqno + " does not "
					+ "match expected sequence number " + window.getPosition());
		}
		if (window.remaining() < payloadSize) {
			throw new ProtocolException("message larger than remaining window size (remaining="
					+ window.remaining() + ",payload size=" + payloadSize + ")");
		}
	}
	
	public synchronized void frameReceived(long seqno, int size) {
		if (seqno != window.getPosition()) {
			throw new IllegalStateException("sequence number " + seqno + " does not "
					+ "match expected sequence number " + window.getPosition());
		}
		
		LOG.debug("frameReceived on channel " + channel + ": seqno=" + seqno + ",size=" + size);
		window.moveBy(size);
		LOG.debug("receiver window = " + window);
		
		if (window.remaining() <= 0.5 * window.getWindowSize()) {
			long ackno = seqno + size;
			int windowSize = window.getWindowSize();
			window.slide(ackno, windowSize);
			LOG.debug("sending SEQ frame on channel " + channel + ": ackno=" + ackno + ",window=" + windowSize);
			LOG.debug("receiver window = " + window);
			transport.sendBytes(createSEQFrame(channel, ackno, windowSize));
		}
	}
	
	private ByteBuffer createSEQFrame(int channel, long ackno, int window) {
		StringBuilder buf = new StringBuilder(SEQHeader.TYPE);
		buf.append(" ");
		buf.append(channel);
		buf.append(" ");
		buf.append(ackno);
		buf.append(" ");
		buf.append(window);
		buf.append("\r\n");
		return ASCII_CHARSET.encode(buf.toString());
	}

	private void enqueueFrame(Frame frame) {
		frames.addLast(frame);
	}
	
	protected int sendFrames(Transport transport) {
		int count = 0;
		Frame frame;
		
		while ((frame = nextFrame()) != null) {
			LOG.debug("send frame " + frame.getHeader());
			senderWindow.moveBy(frame.getSize());
			frame.send(transport);
			LOG.debug("sender window = " + senderWindow);
			count++;
		}
		
		return count;
	}
	
	private Frame nextFrame() {
		if (frames.isEmpty()) {
			return null;
		} else {
			Frame frame = frames.removeFirst();
			
			if (frame.getSize() <= senderWindow.remaining()) {
				LOG.debug("sending frame unchanged (channel=" + channel + ")");
				if (frames.isEmpty()) {
					LOG.debug("sending last frame in buffer (channel=" + channel + ")");
				}
				return frame;
			} else if (senderWindow.remaining() >= MINIMUM_FRAME_SIZE) {
				LOG.debug("split frame at position " + senderWindow.remaining()
						+ " (channel=" + channel + ")");
				Frame[] split = frame.split(senderWindow.remaining());
				frames.addFirst(split[1]);
				return split[0];
			} else {
				frames.addFirst(frame);
				return null;
			}
		}
	}
	
}
