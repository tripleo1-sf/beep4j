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
package net.sf.beep4j.internal.tcp;

/**
 * Helper class to implement calculations of a sliding window.
 * A sliding window has a start property, which signifies the start
 * of the window. It has a position property, which represents the
 * current position within the window. And it has a size property,
 * which specifies how big the window is. The end of the window
 * is calculated. It is equal to start + size. The {@link #remaining()}
 * method returns the number of remaining number of places in the
 * sliding window.
 * 
 * @author Simon Raess
 */
final class SlidingWindow {
	
	static final long MAX = 4294967295L;
	
	private static final long MODULO = MAX + 1;
	
	private long start;
	
	private long position;
	
	private int windowSize;
	
	SlidingWindow(int size) {
		this(0, size);
	}
	
	SlidingWindow(long start, int size) {
		this.start = start;
		this.position = start;
		this.windowSize = size;
	}
	
	int getWindowSize() {
		return windowSize;
	}
	
	long getStart() {
		return start;
	}
	
	long getPosition() {
		return position;
	}
	
	long getEnd() {
		return (start + windowSize) % MODULO;
	}
	
	void slide(long start, int size) {
		start = start % MODULO;
		validateSlide(this.start, start, position, windowSize, size);		
		this.start = start;
		this.windowSize = size;
	}
	
	private void validateSlide(long oldStart, long newStart, long position, 
			int oldWindowSize, int newWindowSize) {
		if (newStart + newWindowSize < oldStart + oldWindowSize) {
			throw new IllegalArgumentException(
					"moving the right window edge to the left is not possible");
		}
		
		if (position >= oldStart) {
			if (newStart < oldStart || position < newStart) {
				throw new IllegalArgumentException("new start ("
						+ newStart + ") must be between old start ("
						+ oldStart + ") and position ("
						+ position + ")");
			}
		} else {
			if (newStart > position && newStart < oldStart) {
				throw new IllegalArgumentException("new start ("
						+ newStart + ") must be between old start ("
						+ oldStart + ") and position ("
						+ position + ")");
			}
		}
	}
	
	void moveBy(int offset) {
		validateMoveBy(offset);
		this.position = (position + offset) % MODULO;
	}

	private void validateMoveBy(int offset) {
		if (position < start) {
			if (position + offset > (start + windowSize) % MODULO) {
				throw new IllegalArgumentException("cannot move position ("
						+ (position + offset) + ") beyond the end of the window ("
						+ (start + windowSize) + ")");
			}
		} else {
			if (position + offset > start + windowSize) {
				throw new IllegalArgumentException("cannot move position ("
						+ (position + offset) + ") beyond the end of the window ("
						+ (start + windowSize) + ")");
			}
		}
	}
	
	int remaining() {
		return (int) (getEnd() - position);
	}
	
	@Override
	public String toString() {
		return "[start=" + start + ",position=" + position + ",window=" + windowSize + "]";
	}
	
}
