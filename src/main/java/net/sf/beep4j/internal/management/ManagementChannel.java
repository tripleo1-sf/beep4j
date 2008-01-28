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

package net.sf.beep4j.internal.management;

import java.util.concurrent.locks.ReentrantLock;

import net.sf.beep4j.Channel;
import net.sf.beep4j.ChannelFilterChainBuilder;
import net.sf.beep4j.internal.session.ChannelImpl;
import net.sf.beep4j.internal.session.InternalSession;

/**
 * Special {@link Channel} implementation to be used for channel 0, also
 * known as the management channel.
 * 
 * @author Simon Raess
 */
public class ManagementChannel extends ChannelImpl {
	
	public ManagementChannel(
			ManagementProfile managementProfile,
			InternalSession session, 
			ChannelFilterChainBuilder filterChainBuilder,
			ReentrantLock sessionLock) {
		
		super(session, null, 0, filterChainBuilder, sessionLock);
		
		registerReplyHandler(0, wrapReplyHandler(new InitialReplyHandler(managementProfile, session)));
	}
	
}
