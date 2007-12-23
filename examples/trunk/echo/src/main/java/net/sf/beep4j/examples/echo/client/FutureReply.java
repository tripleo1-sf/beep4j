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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.sf.beep4j.Message;
import net.sf.beep4j.ReplyHandler;

public class FutureReply<T> implements Future<T>, ReplyHandler {
	
	private final CountDownLatch latch;
	
	private T result;
	
	public FutureReply() {
		this.latch = new CountDownLatch(1);
	}
	
	public boolean cancel(boolean mayInterruptIfRunning) {
		throw new UnsupportedOperationException();
	}

	public T get() throws InterruptedException, ExecutionException {
		latch.await();
		return result;
	}
	
	protected void set(T result) {
		this.result = result;
		this.latch.countDown();
	}

	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (!latch.await(timeout, unit)) {
			throw new TimeoutException();
		}
		return result;
	}

	public boolean isCancelled() {
		return false;
	}

	public boolean isDone() {
		return result != null;
	}
	
	public void receivedANS(Message message) {
		throw new UnsupportedOperationException();
	}
	
	public void receivedNUL() {
		throw new UnsupportedOperationException();
	}
	
	public void receivedERR(Message message) {
		throw new UnsupportedOperationException();
	}
	
	public void receivedRPY(Message message) {
		throw new UnsupportedOperationException();
	}

}
