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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.sf.beep4j.ChannelFilter;
import net.sf.beep4j.Frame;
import net.sf.beep4j.Message;
import net.sf.beep4j.Reply;
import net.sf.beep4j.ext.ChannelFilterAdapter;
import net.sf.beep4j.internal.message.DefaultMessageParser;
import net.sf.beep4j.internal.message.MessageParser;

/**
 * {@link ChannelFilter} that assembles frames into BEEP messages.
 * 
 * @author sir
 */
public class MessageAssemblingFilter extends ChannelFilterAdapter {
	
	private State currentState;
	
	protected static Message createMessage(List<Frame> frames) {
		if (frames.size() == 0) {
			throw new IllegalArgumentException("cannot create message from 0 fragments");
		}
		
		int total = 0;
		for (Frame frame : frames) {
			total += frame.getSize();
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(total);
		for (Frame frame : frames) {
			buffer.put(frame.getByteBuffer());
		}
		buffer.flip();
		
		MessageParser parser = new DefaultMessageParser();
		return parser.parse(buffer);
	}

	@Override
	public void filterMessageReceived(NextFilter next, Object frame, Reply reply) {
		if (currentState == null) {
			currentState = new MSGState(reply);
		}
		currentState.append(next, (Frame) frame);
	}

	@Override
	public void filterReceivedANS(NextFilter next, Object frame) {
		if (currentState == null) {
			currentState = new ANSState();
		}
		currentState.append(next, (Frame) frame);
	}

	@Override
	public void filterReceivedERR(NextFilter next, Object frame) {
		if (currentState == null) {
			currentState = new ERRState();
		}
		currentState.append(next, (Frame) frame);
	}

	public void filterReceivedNUL(NextFilter next) {
		if (currentState == null) {
			currentState = new ANSState();
		}
		currentState.append(next, null);
	}

	public void filterReceivedRPY(NextFilter next, Object frame) {
		if (currentState == null) {
			currentState = new RPYState();
		}
		currentState.append(next, (Frame) frame);
	}
	
	private static interface State {
		
		void append(NextFilter next, Frame frame);
		
	}
	
	private abstract static class SimpleState implements State {
		private final List<Frame> fragments = new LinkedList<Frame>();
		
		public void append(NextFilter next, Frame frame) {
			fragments.add(frame);
			if (!frame.isIntermediate()) {
				forward(next, createMessage(fragments));
			}
		}
		
		protected abstract void forward(NextFilter next, Message message);
	}
	
	private class MSGState extends SimpleState {
		private final Reply reply;
		private MSGState(Reply reply) {
			this.reply = reply;
		}
		@Override
		protected void forward(NextFilter next, Message message) {
			currentState = null;
			next.filterMessageReceived(message, reply);
		}
	}
	
	private class RPYState extends SimpleState {
		@Override
		protected void forward(NextFilter next, Message message) {
			currentState = null;
			next.filterReceivedRPY(message);
		}
	}
	
	private class ERRState extends SimpleState {
		@Override
		protected void forward(NextFilter next, Message message) {
			currentState = null;
			next.filterReceivedERR(message);
		}
	}
	
	private class ANSState implements State {
		private Map<Integer, List<Frame>> fragments = new HashMap<Integer, List<Frame>>();
		
		public void append(NextFilter next, Frame frame) {
			if (frame == null) {
				currentState = null;
				next.filterReceivedNUL();
			} else {
				int answerNumber = frame.getAnswerNumber();
				List<Frame> frames = fragments.get(answerNumber);
				if (frames == null) {
					frames = new LinkedList<Frame>();
					fragments.put(answerNumber, frames);
				}
				frames.add(frame);
				if (!frame.isIntermediate()) {
					fragments.remove(answerNumber);
					next.filterReceivedANS(createMessage(frames));
				}
			}
		}
	}

}
