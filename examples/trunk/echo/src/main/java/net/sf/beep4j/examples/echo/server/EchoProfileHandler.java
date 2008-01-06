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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.sf.beep4j.ChannelHandler;
import net.sf.beep4j.Message;
import net.sf.beep4j.MessageBuilder;
import net.sf.beep4j.Reply;
import net.sf.beep4j.ext.ChannelHandlerAdapter;

/**
 * Simple {@link ChannelHandler} implementation that echoes the received
 * String data back to the sender in a single RPY message.
 * 
 * @author Simon Raess
 */
public class EchoProfileHandler extends ChannelHandlerAdapter {
	
	public static final String PROFILE = "http://www.iserver.ch/profiles/echo";
	
	/**
	 * Copies the input from the received message to the reply.
	 * 
	 * @param message the received message
	 * @param reply the objec used to send a reply
	 */
	@Override
	public void messageReceived(Message message, Reply reply) {
		InputStream stream = message.getInputStream();
		MessageBuilder builder = createMessageBuilder();
		OutputStream os = builder.getOutputStream();
		writeTo(stream, os);
		reply.sendRPY(builder.getMessage());
	}
	
	private void writeTo(InputStream is, OutputStream os) {
		try {
			byte[] buf = new byte[1024];
			int len;
			
			while ((len = is.read(buf)) != -1) {
				os.write(buf, 0, len);
			}
		
			os.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
