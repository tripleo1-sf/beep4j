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

package net.sf.beep4j.internal.session;

import java.nio.ByteBuffer;

import net.sf.beep4j.Frame;
import net.sf.beep4j.MessageType;

/**
 * @author sir
 *
 */
public class FrameStub implements Frame {

	private MessageType type = MessageType.MSG;
	
	private int channelNumber = 1;
	
	private int messageNumber = 1;
	
	private int answerNumber = 1;
	
	private int size;
	
	private boolean intermediate;
	
	private ByteBuffer byteBuffer;
	
	public void setChannelNumber(int channelNumber) {
		this.channelNumber = channelNumber;
	}
	
	public int getChannelNumber() {
		return channelNumber;
	}
	
	public void setMessageNumber(int messageNumber) {
		this.messageNumber = messageNumber;
	}

	public int getMessageNumber() {
		return messageNumber;
	}
	
	public void setSize(int size) {
		this.size = size;
	}

	public int getSize() {
		return size;
	}
	
	public void setType(MessageType type) {
		this.type = type;
	}

	public MessageType getType() {
		return type;
	}
	
	public void setIntermediate(boolean intermediate) {
		this.intermediate = intermediate;
	}

	public boolean isIntermediate() {
		return intermediate;
	}
	
	public void setAnswerNumber(int answerNumber) {
		this.answerNumber = answerNumber;
	}

	public int getAnswerNumber() {
		return answerNumber;
	}

	public ByteBuffer getByteBuffer() {
		if (byteBuffer == null) {
			return ByteBuffer.allocate(getSize());
		} else {
			return byteBuffer.asReadOnlyBuffer();
		}
	}

	public void setByteBuffer(ByteBuffer buffer) {
		this.byteBuffer = buffer.asReadOnlyBuffer();
		this.size = buffer.remaining();
	}

}
