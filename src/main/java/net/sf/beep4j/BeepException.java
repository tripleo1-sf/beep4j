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
package net.sf.beep4j;

/**
 * Exception base class for all exceptions thrown by the BEEP
 * framework
 * 
 * @author Simon Raess
 */
public class BeepException extends RuntimeException {

	private static final long serialVersionUID = 7920530519303604907L;

	public BeepException() {
		super();
	}

	public BeepException(String message, Throwable cause) {
		super(message, cause);
	}

	public BeepException(String message) {
		super(message);
	}

	public BeepException(Throwable cause) {
		super(cause);
	}
	
}
