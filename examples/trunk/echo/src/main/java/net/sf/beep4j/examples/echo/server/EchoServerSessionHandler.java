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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.beep4j.ProfileInfo;
import net.sf.beep4j.SessionHandler;
import net.sf.beep4j.StartChannelRequest;
import net.sf.beep4j.StartSessionRequest;
import net.sf.beep4j.ext.SessionHandlerAdapter;

/**
 * Simple {@link SessionHandler} implementation that shows how to register
 * supported profiles and how to select the effective profile for a new
 * channel. Also shows how data from within the start element can be 
 * used to configure a profile
 * 
 * @author Simon Raess
 */
public class EchoServerSessionHandler extends SessionHandlerAdapter {
	
	private ExecutorService executorService = Executors.newCachedThreadPool();
	
	/**
	 * Registers both the {@link EchoProfileHandler#PROFILE} and the
	 * {@link OneToManyEchoProfileHandler#PROFILE}.
	 */
	@Override
	public void connectionEstablished(StartSessionRequest s) {
		s.registerProfile(EchoProfileHandler.PROFILE);
		s.registerProfile(OneToManyEchoProfileHandler.PROFILE);
		s.registerProfile(EchoEchoProfileHandler.PROFILE);
	}

	/**
	 * Selects a profile for a new channel. Prefers the 
	 * {@link EchoProfileHandler#PROFILE} profile.
	 */
	@Override
	public void channelStartRequested(StartChannelRequest startup) {
		if (startup.hasProfile(EchoProfileHandler.PROFILE)) {
			startup.selectProfile(
					startup.getProfile(EchoProfileHandler.PROFILE),
					new EchoProfileHandler());
		} else if (startup.hasProfile(OneToManyEchoProfileHandler.PROFILE)) {
			ProfileInfo profile = startup.getProfile(OneToManyEchoProfileHandler.PROFILE);
			int size = getSize(profile.getContent());
			startup.selectProfile(
					profile,
					new OneToManyEchoProfileHandler(size));
		} else if (startup.hasProfile(EchoEchoProfileHandler.PROFILE)) {
			startup.selectProfile(
					startup.getProfile(EchoEchoProfileHandler.PROFILE),
					new EchoEchoProfileHandler(executorService));
		}
	}

	private int getSize(String content) {
		try {
			return Integer.parseInt(content.trim());
		} catch (Exception e) {
			return 8192;
		}
	}

}
