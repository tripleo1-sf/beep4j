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
package net.sf.beep4j.internal.stream;

import java.util.HashMap;
import java.util.Map;

import net.sf.beep4j.Frame;
import net.sf.beep4j.ProtocolException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validating {@link FrameHandler} for BEEP sequencing constraints.
 * 
 * @author Simon Raess
 */
public class ValidatingFrameHandler implements FrameHandler {
	
	private static final Logger LOG = LoggerFactory.getLogger(ValidatingFrameHandler.class);
	
	private final FrameHandler handler;
	
	private State currentState;

	public ValidatingFrameHandler(FrameHandler handler) {
		this.handler = handler;
	}
	
	private void setCurrentState(State state) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("moving from " + currentState + " to " + state);
		}
		currentState = state;
	}
	
	// --> start of FrameHandler methods <--
	
	public void receiveMSG(Frame frame) {
		if (currentState == null) {
			setCurrentState(new NormalState(MessageType.MSG));
		}
		
		// pass on to the state
		currentState.append(frame, handler);
		
		// pass to target handler
		handler.receiveMSG(frame);
	}
	
	public void receiveRPY(Frame frame) {
		if (currentState == null) {
			setCurrentState(new NormalState(MessageType.RPY));
		}
		
		// pass on to the state
		currentState.append(frame, handler);

		// pass to target handler
		handler.receiveRPY(frame);
	}
	
	public void receiveERR(Frame frame) {
		if (currentState == null) {
			setCurrentState(new NormalState(MessageType.ERR));
		}
		
		// pass on to the state
		currentState.append(frame, handler);

		// pass to target handler
		handler.receiveERR(frame);
	}
	
	public void receiveANS(Frame frame) {
		if (currentState == null) {
			setCurrentState(new AnsState());
		}
		
		// pass on to the state
		currentState.append(frame, handler);

		// pass to target handler
		handler.receiveANS(frame);
	}
	
	public void receiveNUL(Frame frame) {
		if (currentState == null) {
			setCurrentState(new AnsState());
		}
		
		// pass on to the state
		currentState.append(frame, handler);

		// pass to target handler
		handler.receiveNUL(frame);
	}
	
	// --> end of FrameHandler methods <--
	
	private static interface State {
		
		void append(Frame frame, FrameHandler handler);
		
	}
	
	private class NormalState implements State {
		
		private final MessageType type;
		
		private Frame last;
		
		private long totalSize;
		
		private NormalState(MessageType type) { 
			this.type = type;
		}
		
		private boolean hasPreviousFrame() {
			return last != null;
		}
		
		public void append(Frame frame, FrameHandler handler) {
			if (hasPreviousFrame()) {
				validateMessageNumber(frame);
				validateMatchingFragmentTypes(type, frame.getType());
			}
			
			validateAndIncrementSize(frame.getSize(), frame.getChannelNumber(), frame.getMessageNumber());
			
			if (frame.isIntermediate()) {
				last = frame;
			} else {
				LOG.debug("got complete message");
				last = null;
				setCurrentState(null);
			}
		}
		
		/*
		 * Validates that the cumulative size of all frames for a given message
		 * does not exceed Integer.MAX_VALUE.
		 */
		private void validateAndIncrementSize(int size, int channelNumber, int messageNumber) {
			totalSize += size;
			if (totalSize > Integer.MAX_VALUE) {
				throw new ProtocolException("cumulative frame size exceeds maximum BEEP message size "
						+ "for message " + messageNumber + " on channel " + channelNumber);
			}
		}

		/*
		 * Validation of sequencing according to the BEEP specification section
		 * 2.2.1.1.
		 * 
		 * A frame is poorly formed, if the continuation indicator of the 
		 * previous frame received on the same channel 
		 * was intermediate ("*"), and its message number isn't identical to this frame's 
		 * message number.
		 */
		private void validateMessageNumber(Frame current) {
			if (last.getMessageNumber() != current.getMessageNumber()) {
				throw new ProtocolException("message number for fragments does not match: was "
						+ current.getMessageNumber() + ", should be " 
						+ last.getMessageNumber());
			}
		}
		
		/*
		 * Validation of sequencing according to the BEEP specification section
		 * 2.2.1.1.
		 * 
		 * A frame is poorly formed if the header starts with "MSG", "RPY", "ERR", 
		 * or "ANS", and refers to a message number for which at least one other 
		 * frame has been received, and the three-character keyword starting this 
		 * frame and the immediately-previous received frame for this message 
		 * number are not identical
		 */
		private void validateMatchingFragmentTypes(MessageType last, MessageType current) {
			if (MessageType.ERR == current
					|| MessageType.MSG == current
					|| MessageType.RPY == current) {
				if (!last.equals(current)) {
					throw new ProtocolException("header type does not match: expected "
							+ last + " but was " + current);
				}
			}
		}
		
		@Override
		public String toString() {
			return "<normal>";
		}
		
	}
	
	private class AnsState implements State {
		
		private final Map<Integer, Long> totalSizes = new HashMap<Integer, Long>();
		
		private int messageNumber = -1;
		
		public void append(Frame frame, FrameHandler handler) {
			MessageType type = frame.getType();
			
			if (messageNumber == -1) {
				messageNumber = frame.getMessageNumber();
			} else {
				validateMessageNumber(frame);
			}
			
			if (MessageType.ANS == type) {
				validateAndIncrementMessageSize(frame);

				if (!frame.isIntermediate()) {
					totalSizes.remove(frame.getAnswerNumber());
				}
				
			} else if (MessageType.NUL == type) {
				if (hasUnfinishedAnsMessages()) {
					// Validation of sequencing according to the BEEP specification section
					// 2.2.1.1.
					//  
					// A frame is poorly formed if the header starts with "NUL", and refers to 
					// a message number for which at least one other frame has been received, 
					// and the keyword of of the immediately-previous received frame for 
					// this reply isn't "ANS".					
					throw new ProtocolException("unfinished ANS messages");
				} else if (frame.isIntermediate()) {
					throw new ProtocolException("NUL reply's continuation indicator is '*'");
				} else if (frame.getSize() != 0) {
					throw new ProtocolException("NUL reply's payload size is non-zero ("
							+ frame.getSize() + ")");
				}
				
				setCurrentState(null);
				
			} else {
				throw new ProtocolException("expected ANS or NUL message, was " + type.name());
			}			
		}
		
		private void validateAndIncrementMessageSize(Frame frame) {
			Long totalSize = totalSizes.get(frame.getAnswerNumber());
			if (totalSize == null) {
				totalSize = 0l;
			}
			
			totalSize += frame.getSize();
			totalSizes.put(frame.getAnswerNumber(), totalSize);
		}

		/*
		 * Validation of sequencing according to the BEEP specification section
		 * 2.2.1.1.
		 * 
		 * A frame is poorly formed, if the continuation indicator of the 
		 * previous frame received on the same channel 
		 * was intermediate ("*"), and its message number isn't identical to this frame's 
		 * message number.
		 */
		private void validateMessageNumber(Frame current) {
			if (messageNumber != current.getMessageNumber()) {
				throw new ProtocolException("message number for fragments does not match: was "
						+ current.getMessageNumber() + ", should be " 
						+ messageNumber);
			}
		}
				
		private boolean hasUnfinishedAnsMessages() {
			return totalSizes.size() > 0;
		}
		
		@Override
		public String toString() {
			return "<ANS>";
		}
		
	}
	
}
