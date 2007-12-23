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

import net.sf.beep4j.CloseChannelCallback;

public class CloseChannelFuture extends ResultFuture<Boolean> implements CloseChannelCallback {

	public void closeAccepted() {
		set(Boolean.TRUE);
	}

	public void closeDeclined(int code, String message) {
		set(Boolean.FALSE);
	}

}
