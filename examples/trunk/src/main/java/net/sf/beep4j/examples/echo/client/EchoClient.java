/*
 *  Copyright 2007 Simon Raess
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
package net.sf.beep4j.examples.echo.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import net.sf.beep4j.Channel;
import net.sf.beep4j.Message;
import net.sf.beep4j.MessageBuilder;
import net.sf.beep4j.ProfileInfo;
import net.sf.beep4j.Session;
import net.sf.beep4j.examples.echo.server.EchoProfileHandler;
import net.sf.beep4j.examples.echo.server.OneToManyEchoProfileHandler;
import net.sf.beep4j.ext.ChannelHandlerAdapter;
import net.sf.beep4j.ext.SessionHandlerAdapter;
import net.sf.beep4j.transport.mina.MinaInitiator;

import org.apache.mina.common.IoConnector;
import org.apache.mina.transport.socket.nio.SocketConnector;

/**
 * A simple example echo client. Shows how to open channels, send messages and receive
 * replies, close channels again and close session. Heavily uses custom {@link Future} 
 * implementations to get all code inside one class. Using the standard asynchronous
 * API is recommended for most applications.
 * 
 * @author Simon Raess
 */
public class EchoClient extends SessionHandlerAdapter {

	private final int port;
	
	private ResultFuture<Session> connectFuture;
	
	public EchoClient(int port) {
		this.port = port;
	}
	
	/**
	 * Connects to the BEEP listener that listens on the specified port
	 * on localhost.
	 * 
	 * @return a Future that contains the {@link Session} as result
	 */
	public Future<Session> connect() {
		IoConnector connector = new SocketConnector();
		MinaInitiator initiator = new MinaInitiator(connector);
		SocketAddress address = new InetSocketAddress(port);
		initiator.connect(address, this);
		connectFuture = new ResultFuture<Session>();
		return connectFuture;
	}
	
	/**
	 * Notification that the {@link Session} was opened. Completes the
	 * Future returned from {@link #connect()}.
	 */
	@Override
	public void sessionOpened(Session session) {
		super.sessionOpened(session);
		this.connectFuture.set(session);
	}
	
	/**
	 * Notification that the {@link Session} was closed.
	 */
	@Override
	public void sessionClosed() {
		super.sessionClosed();
		System.exit(0);
	}
	
	/**
	 * Starts a channel on {@link Session} using the specified <var>profile</var>.
	 * 
	 * @param profile the profile of the channel
	 * @return a Future that returns the Channel
	 */
	public Future<Channel> startChannel(ProfileInfo profile) {
		final ResultFuture<Channel> future = new ResultFuture<Channel>();
		getSession().startChannel(profile, new ChannelHandlerAdapter() {
			@Override
			public void channelOpened(Channel channel) {
				super.channelOpened(channel);
				future.set(channel);
			}
		});
		return future;
	}
	
	public static void main(String[] args) throws Exception {		
		int port = readPort(args);
		
		configureLogging();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		
		EchoClient client = new EchoClient(port);
		Session session = client.connect().get();
		
		// use the two available echo profiles
		ProfileInfo[] profile = new ProfileInfo[2];
		profile[0] = new ProfileInfo(OneToManyEchoProfileHandler.PROFILE, "128");
		profile[1] = new ProfileInfo(EchoProfileHandler.PROFILE);
		
		final String message = loadMessage("rfc3080.txt");
		
		// open some channels using the profiles specified above
		// do some work in parallel (using executor service), the Session is
		// be thread-safe
		for (int i = 0; i < 5; i++) {
			List<Future<String>> futures = new ArrayList<Future<String>>();
			EchoSender[] sender = new EchoSender[2];
			for (int j = 0; j < sender.length; j++) {
				sender[j] = new EchoSender(client, profile[j % 2], message);
				futures.add(j, executor.submit(sender[j]));
			}
			for (int j = 0; j < sender.length; j++) {
				futures.get(j).get();
			}
		}
		
		// well, enough done, close the sesion
		session.close();
	}
	
	private static int readPort(String[] args) {
		int port = 8888;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		}
		return port;
	}
	
	/**
	 * Configures the logging so that all information passing through 
	 * the transport to a log file called data.log. This might be 
	 * interesting to see what's going on on the underlying transport.
	 * 
	 * @throws IOException
	 */
	private static void configureLogging() throws IOException {
		Logger logger = Logger.getLogger("net.sf.beep4j.transport.DATA");
		logger.setLevel(Level.ALL);
		FileHandler handler = new FileHandler("data.log");
		handler.setFormatter(new SimpleFormatter());
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
	}
	
	/**
	 * Load a message String from a resource on the classpath.
	 * 
	 * @param resource the path of the resource
	 * @return the String content of that resource
	 * @throws IOException if reading the resource fails
	 */
	private static String loadMessage(String resource) throws IOException {
		InputStream stream = EchoClient.class.getResourceAsStream(resource);
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "ISO-8859-1"));
		StringBuilder builder = new StringBuilder();
		
		char[] buf = new char[1024];
		int count;
		
		while ((count = reader.read(buf)) != -1) {
			builder.append(buf, 0, count);
		}
		
		reader.close();
		return builder.toString();
	}
	
	/**
	 * {@link Callable} implementation that opens a channel for the specified
	 * profile, sends a message over that channel and reads the response.
	 * The received response text is compared to the sent message text. If
	 * they do not correspond then an exception is thrown. At the end the
	 * channel is closed again.
	 */
	private static class EchoSender implements Callable<String> {
		private final EchoClient client;
		private final ProfileInfo profile;
		private final String message;
		
		private EchoSender(EchoClient client, ProfileInfo profile, String message) {
			this.client = client;
			this.profile = profile;
			this.message = message;
		}
		
		public String call() throws Exception {
			Channel channel = client.startChannel(profile).get();
			FutureReply<String> reply = new FutureStringReply();
			channel.sendMessage(createMessage(channel.createMessageBuilder(), message), reply);
			String replyText = reply.get();
			
			if (!message.equals(replyText)) {
				throw new RuntimeException("the response is not equal to the request");
			}
			
			CloseChannelFuture closeFuture = new CloseChannelFuture();
			channel.close(closeFuture);
			closeFuture.get();
			
			return replyText;
		}
		
		/**
		 * Creates a {@link Message} object from a String. The String is written
		 * using the ISO-8859-1 charset.
		 * 
		 * @param builder the message builder used to build the message
		 * @param message the String message to be put into the message
		 * @return the Message object containing the given String message
		 */
		private Message createMessage(MessageBuilder builder, String message) {
			builder.setContentType("text", "plain");
			builder.setCharsetName("ISO-8859-1");
			PrintWriter writer = new PrintWriter(builder.getWriter());
			writer.print(message);
			writer.close();
			return builder.getMessage();
		}
	}

	/**
	 * A FutureReply that treats the received message as String. It reads the message
	 * using ISO-8859-1 encoding. Instances are capable to get the result either
	 * from a single RPY reply or from multiple ANS replies.
	 */
	protected static final class FutureStringReply extends FutureReply<String> {
		private final StringBuilder builder = new StringBuilder();

		@Override
		public void receivedRPY(Message message) {
			set(readFully(message));
		}

		@Override
		public void receivedANS(Message message) {
			builder.append(readFully(message));
		}

		@Override
		public void receivedNUL() {
			set(builder.toString());
		}

		private String readFully(Message message) {
			try {
				StringBuilder builder = new StringBuilder();
				BufferedReader reader = new BufferedReader(message.getReader("ISO-8859-1"));
				char[] cbuf = new char[1024];
				int len = 0;
				while ((len = reader.read(cbuf)) != -1) {
					builder.append(cbuf, 0, len);
				}
				reader.close();
				return builder.toString();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}			
	}
	
}
