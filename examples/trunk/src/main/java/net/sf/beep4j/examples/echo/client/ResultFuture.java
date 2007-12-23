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

class ResultFuture<T> implements Future<T> {
	private final CountDownLatch latch;
	private T result;
	public ResultFuture() {
		this.latch = new CountDownLatch(1);
	}
	public boolean cancel(boolean mayInterruptIfRunning) {
		throw new UnsupportedOperationException();
	}
	public void set(T result) {
		this.result = result;
		this.latch.countDown();
	}
	public T get() throws InterruptedException, ExecutionException {
		latch.await();
		return result;
	}

	public T get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException,
			TimeoutException {
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
	
}