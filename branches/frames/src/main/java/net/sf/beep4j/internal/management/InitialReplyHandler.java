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

import net.sf.beep4j.Message;
import net.sf.beep4j.ProtocolException;
import net.sf.beep4j.ReplyHandler;
import net.sf.beep4j.internal.session.InternalSession;
import net.sf.beep4j.internal.util.Assert;

/**
 * {@link ReplyHandler} implementation to be used for the initial reply
 * on channel 0. This is either a BEEP greeting or an error.
 * 
 * @author Simon Raess
 */
class InitialReplyHandler implements ReplyHandler {
	
	private final ManagementProfile profile;
	
	private final InternalSession session;
	
	public InitialReplyHandler(ManagementProfile profile, InternalSession session) {
		Assert.notNull("profile", profile);
		Assert.notNull("session", session);
		this.profile = profile;
		this.session = session;
	}
	
	public void receivedRPY(Object msg) {
		Message message = (Message) msg;
		Greeting greeting = profile.receivedGreeting(message);
		session.sessionStartAccepted(greeting);
	}
	
	public void receivedERR(Object msg) {
		Message message = (Message) msg;
		BEEPError error = profile.receivedError(message);
		session.sessionStartDeclined(error.getCode(), error.getMessage());
	}
	
	public void receivedANS(Object message) {
		throw new ProtocolException("the first message must either be of type RPY or ERR");
	}
	
	public void receivedNUL() {
		throw new ProtocolException("the first message must either be of type RPY or ERR");
	}
	
}
