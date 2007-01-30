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
package net.sf.beep4j.internal;

import net.sf.beep4j.CloseChannelCallback;

public class VerifyingCloseChannelCallback implements CloseChannelCallback {
	private final CloseChannelCallback target;
	private boolean complete;
	
	public VerifyingCloseChannelCallback(CloseChannelCallback callback) {
		this.target = callback;
	}
	
	public void verify() {
		if (!complete) {
			throw new IllegalStateException("callback is not complete");
		}
	}
	
	public void closeAccepted() {
		setComplete();
		target.closeAccepted();
	}
	
	public void closeDeclined(int code, String message) {
		setComplete();
		target.closeDeclined(code, message);
	}
	
	private void setComplete() {
		if (complete) {
			throw new IllegalStateException("callback is complete");
		}
		complete = true;
	}
}