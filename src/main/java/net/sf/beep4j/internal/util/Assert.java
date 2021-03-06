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
package net.sf.beep4j.internal.util;

import java.util.concurrent.locks.ReentrantLock;

public final class Assert {
	
	private Assert() {
		// hidden constructor
	}
	
	/**
	 * Asserts that the given value is not null. Throws an
	 * IllegalArgumentException if the value is null.
	 * 
	 * @param name the name of the value (e.g. a parameter name)
	 * @param value the value that must be null
	 */
	public static final void notNull(String name, Object value) {
		if (value == null) {
			throw new IllegalArgumentException(name + " cannot be null");
		}
	}
	
	public static final void holdsLock(String name, ReentrantLock lock) {
		if (lock != null && !lock.isHeldByCurrentThread()) {
			throw new IllegalStateException("current thread does not hold " + name + " lock");
		}
	}
	
}
