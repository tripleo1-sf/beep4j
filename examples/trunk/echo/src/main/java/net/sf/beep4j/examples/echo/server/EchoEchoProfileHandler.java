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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

import net.sf.beep4j.Channel;
import net.sf.beep4j.Message;
import net.sf.beep4j.MessageBuilder;
import net.sf.beep4j.Reply;
import net.sf.beep4j.ext.ChannelHandlerAdapter;
import net.sf.beep4j.ext.ReplyHandlerAdapter;

public class EchoEchoProfileHandler extends ChannelHandlerAdapter {
	
	public static final String PROFILE = "http://www.iserver.ch/profiles/echo-echo";
	
	private final ExecutorService executorService;
	
	public EchoEchoProfileHandler(ExecutorService executorService) {
		this.executorService = executorService;
	}
	
	/**
	 * Copies the input from the received message to the reply.
	 * 
	 * @param message the received message
	 * @param reply the object used to send a reply
	 */
	@Override
	public void messageReceived(Message message, Reply reply) {
		InputStream stream = message.getInputStream();
		
		// first copy to byte array, we want to send back an echo of our own...
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		writeTo(stream, os);
		final byte[] bytes = os.toByteArray();
		
		// then execute a send echo task
		executorService.execute(new EchoSender(getChannel(), bytes));
		
		MessageBuilder builder = createMessageBuilder();
		OutputStream target = builder.getOutputStream();
		writeTo(new ByteArrayInputStream(bytes), target);
		
		reply.sendRPY(builder.getMessage());
	}
	
	private static void writeTo(InputStream is, OutputStream os) {
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
	
	private static class EchoSender implements Runnable {
		private final Channel channel;
		private final byte[] bytes;
		private EchoSender(Channel channel, byte[] bytes) {
			this.channel = channel;
			this.bytes = bytes;
		}
		public void run() {
			MessageBuilder messageBuilder = channel.createMessageBuilder();
			writeTo(new ByteArrayInputStream(bytes), messageBuilder.getOutputStream());
			channel.sendMessage(messageBuilder.getMessage(), new ReplyHandlerAdapter() {
			
				public void receivedRPY(Message message) {
					verify(bytes, message.getInputStream());
				}
			
			});
		}
		private void verify(byte[] bytes, InputStream is) {
			try {
				int pos = 0;
				int c;
				while ((c = is.read()) != -1) {
					if ((c & 0xFF) != bytes[pos++]) {
						System.err.println("invalid content received");
						break;
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
