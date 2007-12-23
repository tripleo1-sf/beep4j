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
package net.sf.beep4j.examples.echo.server;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import net.sf.beep4j.SessionHandler;
import net.sf.beep4j.SessionHandlerFactory;
import net.sf.beep4j.transport.mina.MinaListener;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptor;

/**
 * Simple echo server implementation showing how to create a BEEP listener
 * peer and how to specify the SessionHandler for the incoming 
 * connections.
 * 
 * @author Simon Raess
 */
public class EchoServer {
	
	private final int port;
	
	public EchoServer(int port) {
		this.port = port;
	}
	
	public void start() throws Exception {
		IoAcceptor acceptor = new SocketAcceptor();
		SocketAddress address = new InetSocketAddress(port);

		MinaListener listener = new MinaListener(acceptor);
		listener.bind(address, new SessionHandlerFactory() {
			public SessionHandler createSessionHandler() {
				return new EchoServerSessionHandler();
			}
		});
	}

	public static void main(String[] args) throws Exception {
		int port = 8888;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		}
		
		EchoServer server = new EchoServer(port);
		server.start();
		System.out.println("... server started, listening on port " + port);
	}
	
}
