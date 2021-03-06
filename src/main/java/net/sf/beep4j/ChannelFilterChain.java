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
 * Represents a mutable chain of filters. The first filter in the chain is closest
 * to the network, the last filter in the chain is closest to the application.
 * Depending on the filter method of the interface {@link ChannelFilter} the
 * chain starts processing at the head (incoming requests) or the tail
 * (outgoing requests).
 * 
 * @author Simon Raess
 */
public interface ChannelFilterChain {
	
	void addFirst(ChannelFilter filter);
	
	void addLast(ChannelFilter filter);
	
	void addAfter(Class<? extends ChannelFilter> after, ChannelFilter filter);
	
	void addBefore(Class<? extends ChannelFilter> before, ChannelFilter filter);
	
}
