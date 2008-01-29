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
package net.sf.beep4j.filters;

import java.util.HashMap;
import java.util.Map;

import net.sf.beep4j.Channel;
import net.sf.beep4j.Frame;
import net.sf.beep4j.Reply;
import net.sf.beep4j.ext.ChannelFilterAdapter;

/**
 * Filter implementation that can be used to limit the size of incoming messages.
 * Implement {@link #handleMessageSizeOverflow()} to implement a specific
 * handling of this situation.
 * 
 * @author Simon Raess
 */
public abstract class MessageSizeLimittingFilter extends ChannelFilterAdapter {
	
	private final int maxMessageSize;
	
	private State currentState;
	
	protected Channel channel;
	
	public MessageSizeLimittingFilter(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}
	
	@Override
	public void filterChannelOpened(NextFilter next, Channel channel) {
		super.filterChannelOpened(next, channel);
		this.channel = channel;
	}
	
	@Override
	public void filterMessageReceived(NextFilter next, Object msg, Reply reply) {
		handleSimpleFrameType((Frame) msg);
		super.filterMessageReceived(next, msg, reply);
	}
	
	@Override
	public void filterReceivedANS(NextFilter next, Object msg) {
		handleComplexFrameType((Frame) msg);
		super.filterReceivedANS(next, msg);
	}
	
	@Override
	public void filterReceivedNUL(NextFilter next) {
		handleComplexFrameType(null);
		super.filterReceivedNUL(next);
	}
	
	@Override
	public void filterReceivedERR(NextFilter next, Object msg) {
		handleSimpleFrameType((Frame) msg);
		super.filterReceivedERR(next, msg);
	}
	
	public void filterReceivedRPY(NextFilter next, Object msg) {
		handleSimpleFrameType((Frame) msg);
		super.filterReceivedRPY(next, msg);
	}
	
	private void handleSimpleFrameType(Frame frame) {
		if (currentState == null) {
			currentState = new SimpleState(maxMessageSize);
		}
		currentState.frameReceived(frame);
		if (!frame.isIntermediate()) {
			currentState = null;
		}
	}
	
	private void handleComplexFrameType(Frame frame) {
		if (currentState == null) {
			currentState = new ANSState(maxMessageSize);
		}
		if (frame != null) {
			currentState.frameReceived(frame);
		} else {
			currentState = null;
		}
	}
	
	protected abstract void handleMessageSizeOverflow(Channel channel);
	
	private static interface State {
		
		void frameReceived(Frame frame);
		
	}
	
	private class SimpleState implements State {
		
		private final int maxMessageSize;
		
		private int messageSize;
		
		protected SimpleState(int maxMessageSize) {
			this.maxMessageSize = maxMessageSize;
		}
		
		public void frameReceived(Frame frame) {
			messageSize += frame.getSize();
			if (messageSize > maxMessageSize) {
				handleMessageSizeOverflow(channel);
			}
		}
		
	}

	private class ANSState implements State {
		
		private final int maxMessageSize;
		
		private final Map<Integer, Integer> messageSizes = new HashMap<Integer, Integer>();
		
		protected ANSState(int maxMessageSize) {
			this.maxMessageSize = maxMessageSize;
		}
		
		public void frameReceived(Frame frame) {
			Integer messageSize = messageSizes.get(frame.getAnswerNumber());
			if (messageSize == null) {
				messageSize = Integer.valueOf(0);
				messageSizes.put(frame.getAnswerNumber(), messageSize);
			}
			int currentSize = messageSize + frame.getSize();
			messageSizes.put(frame.getAnswerNumber(), currentSize);
			
			if (currentSize > maxMessageSize) {
				handleMessageSizeOverflow(channel);
			}
		}
		
	}
	
}
