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
package net.sf.beep4j.examples.echo.server;

import java.nio.ByteBuffer;

import net.sf.beep4j.ChannelHandler;
import net.sf.beep4j.Message;
import net.sf.beep4j.MessageBuilder;
import net.sf.beep4j.Reply;
import net.sf.beep4j.ext.ChannelHandlerAdapter;

/**
 * Simple {@link ChannelHandler} implementation that echoes the received
 * data in several ANS messages. How many data that is packed into a 
 * given ANS message is determined by an integer passed as initialization
 * data in the start element.
 * 
 * @author Simon Raess
 */
public class OneToManyEchoProfileHandler extends ChannelHandlerAdapter {
	
	public static final String PROFILE = "http://www.iserver.ch/profiles/echo-2";
	
	private final int blockSize;
	
	public OneToManyEchoProfileHandler(int blockSize) {
		this.blockSize = blockSize;
	}
	
	/**
	 * Echoes the received data back to the sender in ANS
	 * messages of size {@link #blockSize}.
	 * 
	 * @param message the received message
	 * @param reply the reply object used to send the reply
	 */
	public void messageReceived(Message message, Reply reply) {
		ByteBuffer buffer = message.getContentBuffer();
		int remaining = buffer.remaining();
		
		while (remaining > 0) {
			MessageBuilder builder = reply.createMessageBuilder();
			int size = Math.min(blockSize, remaining);
			buffer.limit(buffer.position() + size);
			builder.getContentBuffer(size).put(buffer);
			reply.sendANS(builder.getMessage());
			remaining -= size;
		}
		
		// after all the ANS replies have sent a NUL must be sent
		reply.sendNUL();
	}

}
