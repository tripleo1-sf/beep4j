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
package net.sf.beep4j.internal.stream;

import junit.framework.TestCase;
import net.sf.beep4j.MessageType;
import net.sf.beep4j.ProtocolException;
import net.sf.beep4j.internal.session.FrameStub;

import org.easymock.MockControl;

public class ValidatingFrameHandlerTest extends TestCase {
	
	private MockControl control;
	private FrameHandler handler;
	private ValidatingFrameHandler target;

	@Override
	protected void setUp() throws Exception {
		control = MockControl.createStrictControl(FrameHandler.class);
		control.setDefaultMatcher(MockControl.ALWAYS_MATCHER);
		handler = (FrameHandler) control.getMock();
		target = new ValidatingFrameHandler(handler);
	}
	
	@Override
	protected void tearDown() throws Exception {
		control.verify();
	}
	
	public void testMSGNonFragmented() throws Exception {
		handler.receiveMSG(createMSG(0, 0, false));
		handler.receiveMSG(createMSG(0, 1, false));
		control.replay();
		
		// test		
		target.receiveMSG(createMSG(0, 0, false));
		target.receiveMSG(createMSG(0, 1, false));
	}
	
	public void testMSGFragmented() throws Exception {
		handler.receiveMSG(createMSG(0, 0, true));
		handler.receiveMSG(createMSG(0, 0, false));
		handler.receiveMSG(createMSG(0, 0, true));
		control.replay();
		
		// test
		target.receiveMSG(createMSG(0, 0, true));
		target.receiveMSG(createMSG(0, 0, false));
		target.receiveMSG(createMSG(0, 0, true));
	}
	
	public void testMSGMessageNumberMismatch() throws Exception {
		handler.receiveMSG(createMSG(0, 0, true));
		control.replay();
		
		// test
		target.receiveMSG(createMSG(0, 0, true));
		
		try {
			target.receiveMSG(createMSG(0, 1, false));
			fail("message numbers are not equal");
		} catch (Exception e) {
			// expected
		}
	}
	
	public void testMSGInvalidSuccessor() throws Exception {
		handler.receiveMSG(createMSG(1, 0, true));
		control.replay();
		
		// test
		target.receiveMSG(createMSG(1, 0, true));
		
		try {
			target.receiveRPY(createRPY(1, 0, false));
			fail("invalid successor not detected");
		} catch (Exception e) {
			// expected
		}
	}
	
	public void testANSNonFragmented() throws Exception {
		handler.receiveANS(createANS(1, 0, false, 0));
		handler.receiveANS(createANS(1, 0, false, 1));
		handler.receiveNUL(createNUL(1, 0));
		control.replay();
		
		// test
		target.receiveANS(createANS(1, 0, false, 0));		
		target.receiveANS(createANS(1, 0, false, 1));
		target.receiveNUL(createNUL(1, 0));
	}
	
	public void testANSFragmented() throws Exception {
		handler.receiveANS(createANS(1, 0, true, 0));		
		handler.receiveANS(createANS(1, 0, true, 0));		
		handler.receiveANS(createANS(1, 0, false, 0));		
		handler.receiveNUL(createNUL(1, 0));
		control.replay();
		
		// test
		target.receiveANS(createANS(1, 0, true, 0));		
		target.receiveANS(createANS(1, 0, true, 0));		
		target.receiveANS(createANS(1, 0, false, 0));		
		target.receiveNUL(createNUL(1, 0));
	}
	
	public void testANSInterleaved() throws Exception {
		handler.receiveANS(createANS(1, 0, true, 0));
		handler.receiveANS(createANS(1, 0, true, 1));		
		handler.receiveANS(createANS(1, 0, false, 0));		
		handler.receiveANS(createANS(1, 0, false, 1));		
		handler.receiveNUL(createNUL(1, 0));
		control.replay();
		
		// test
		target.receiveANS(createANS(1, 0, true, 0));
		target.receiveANS(createANS(1, 0, true, 1));		
		target.receiveANS(createANS(1, 0, false, 0));		
		target.receiveANS(createANS(1, 0, false, 1));		
		target.receiveNUL(createNUL(1, 0));
	}

	public void testANSInvalidSuccessor() throws Exception {
		handler.receiveANS(createANS(1, 0, true, 0));
		control.replay();
		
		// test
		target.receiveANS(createANS(1, 0, true, 0));
		
		try {
			target.receiveRPY(createRPY(1, 0, false));
			fail("ANS or NUL expected");
		} catch (ProtocolException e) {
			// expected
		}
	}
	
	public void testANSMessageNumberMismatch() throws Exception {
		handler.receiveANS(createANS(1, 0, false, 0));
		control.replay();
		
		// test
		target.receiveANS(createANS(1, 0, false, 0));
		
		try {
			target.receiveANS(createANS(1, 1, false, 1));
			fail("message number mismatch");
		} catch (ProtocolException e) {
			// expected
		}
	}
	
	public void testANSUnfinishedAnswers() throws Exception {
		handler.receiveANS(createANS(1, 0, true, 0));
		control.replay();
		
		// test
		target.receiveANS(createANS(1, 0, true, 0));
		
		try {
			target.receiveNUL(createNUL(1, 0));
			fail("response has unfinished ANS messages");
		} catch (ProtocolException e) {
			// expected
		}
	}
	
	public void testNUL() throws Exception {
		handler.receiveNUL(createNUL(1, 0));
		control.replay();
		
		// test
		target.receiveNUL(createNUL(1, 0));
		
		// verify
		control.verify();
	}
	
	public void testNULInvalid() throws Exception {
		control.replay();
		
		// test
		FrameStub frame = createNUL(1, 0);
		frame.setIntermediate(true);
		
		try {
			target.receiveNUL(frame);
			fail("NUL frame cannot have continuation indicator set to true");
		} catch (ProtocolException e) {
			// expected
		}
		
		frame = createNUL(1, 0);
		frame.setSize(20);
		
		try {
			target.receiveNUL(frame);
			fail("NUL frame cannot have non-zero size");
		} catch (ProtocolException e) {
			// expected
			// TODO: correct exception type
		}

		// verify
		control.verify();
	}
	
	private FrameStub createNUL(int channelNumber, int messageNumber) {
		FrameStub frame = new FrameStub();
		frame.setType(MessageType.NUL);
		frame.setChannelNumber(channelNumber);
		frame.setMessageNumber(messageNumber);
		return frame;
	}
	
	private FrameStub createANS(int channelNumber, int messageNumber, boolean intermediate, int answerNumber) {
		FrameStub frame = new FrameStub();
		frame.setType(MessageType.ANS);
		frame.setChannelNumber(channelNumber);
		frame.setMessageNumber(messageNumber);
		frame.setIntermediate(intermediate);
		frame.setAnswerNumber(answerNumber);
		return frame;
	}

	private FrameStub createRPY(int channelNumber, int messageNumber, boolean intermediate) {
		FrameStub frame = new FrameStub();
		frame.setType(MessageType.RPY);
		frame.setChannelNumber(channelNumber);
		frame.setMessageNumber(messageNumber);
		frame.setIntermediate(intermediate);
		return frame;
	}

	private FrameStub createMSG(int channelNumber, int messageNumber, boolean intermediate) {
		FrameStub frame = new FrameStub();
		frame.setType(MessageType.MSG);
		frame.setChannelNumber(channelNumber);
		frame.setMessageNumber(messageNumber);
		frame.setIntermediate(intermediate);
		return frame;
	}

}
