/*
 *  Copyright 2008 Simon Raess
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

import junit.framework.TestCase;

import org.jmock.Mockery;
import org.jmock.integration.junit3.JUnit3Mockery;

public class ManagementChannelHandlerTest extends TestCase {
	
	private Mockery context;
	
	private ManagementProfile profile;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		context = new JUnit3Mockery();
		profile = context.mock(ManagementProfile.class);
	}
	
	public void testReceiveGreeting() throws Exception {
		ManagementMessageParser parser = new SaxMessageParser();
		ManagementChannelHandler target = new ManagementChannelHandler(profile, parser);
		
		target.channelClosed();
	}
	
}
