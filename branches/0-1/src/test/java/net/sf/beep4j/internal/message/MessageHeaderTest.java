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
package net.sf.beep4j.internal.message;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import junit.framework.TestCase;

public class MessageHeaderTest extends TestCase {
	
	private MessageHeader header = new MessageHeader();
	
	public void testWithDefaultContentType() throws Exception {
		assertHeaderEquals("\r\n");
	}
	
	public void testWithCustomContentType() throws Exception {
		header.setContentType("text", "plain");
		assertHeaderEquals("Content-Type: text/plain\r\n\r\n");
	}
	
	public void testWithCustomCharset() throws Exception {
		header.setContentType("text", "plain");
		header.setCharset("ISO-8859-1");
		assertHeaderEquals("Content-Type: text/plain; charset=ISO-8859-1\r\n\r\n");
	}
	
	public void testWithCustomHeaders() throws Exception {
		header.addHeader("Foo", "Bar");
		header.addHeader("Another", "Test");
		assertHeaderEquals("Foo: Bar\r\nAnother: Test\r\n\r\n");
	}

	private void assertHeaderEquals(String expected) {
		String content = convertToString(header);
		assertEquals(expected, content);
	}

	private String convertToString(MessageHeader header) {
		ByteBuffer bbuf = header.asByteBuffer();
		CharBuffer cbuf = Charset.forName("US-ASCII").decode(bbuf);
		final String content = cbuf.toString();
		return content;
	}
	
}
