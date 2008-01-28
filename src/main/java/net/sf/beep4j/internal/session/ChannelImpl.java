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
package net.sf.beep4j.internal.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import net.sf.beep4j.Channel;
import net.sf.beep4j.ChannelFilterChainBuilder;
import net.sf.beep4j.ChannelHandler;
import net.sf.beep4j.CloseChannelCallback;
import net.sf.beep4j.CloseChannelRequest;
import net.sf.beep4j.Frame;
import net.sf.beep4j.Message;
import net.sf.beep4j.MessageBuilder;
import net.sf.beep4j.ProfileInfo;
import net.sf.beep4j.ProtocolException;
import net.sf.beep4j.Reply;
import net.sf.beep4j.ReplyHandler;
import net.sf.beep4j.Session;
import net.sf.beep4j.ext.ChannelFilterAdapter;
import net.sf.beep4j.internal.DefaultChannelFilterChain;
import net.sf.beep4j.internal.FilterChainTargetHolder;
import net.sf.beep4j.internal.management.CloseCallback;
import net.sf.beep4j.internal.message.DefaultMessageBuilder;
import net.sf.beep4j.internal.util.Assert;
import net.sf.beep4j.internal.util.IntegerSequence;
import net.sf.beep4j.internal.util.Sequence;

public class ChannelImpl implements Channel, InternalChannel {
	
	protected final InternalSession session;
	
	private final String profile;
	
	private final int channelNumber;
	
	private final InternalChannelFilterChain filterChain;
	
	private final ReentrantLock sessionLock;
	
	private final Sequence<Integer> messageNumberSequence = new IntegerSequence(1, 1);

	/**
	 * Maps from message number to Reply objects. Replies are registered when they are
	 * created and removed from this map as soon as they are completed by the
	 * application.
	 */
	private final Map<Integer, Reply> replies = Collections.synchronizedMap(new HashMap<Integer, Reply>());
	
	private final LinkedList<ReplyHandlerHolder> replyHandlerHolders = new LinkedList<ReplyHandlerHolder>();
	
	private ChannelHandler channelHandler;
	
	private State state = new Alive();
	
	/**
	 * Counter that counts how many messages we have sent but to which we
	 * have not received a reply.
	 */
	private int openOutgoingReplies;
	
	/**
	 * Counter that counts how many messages we have received but to which
	 * we have not sent a response.
	 */
	private int openIncomingReplies;
	
	public ChannelImpl(
			InternalSession session, 
			ProfileInfo profile, 
			int channelNumber,
			ChannelFilterChainBuilder filterChainBuilder,
			ReentrantLock sessionLock) {
		this.session = session;
		this.profile = profile != null ? profile.getUri() : null;
		this.channelNumber = channelNumber;
		this.sessionLock = sessionLock;
		this.filterChain = new DefaultChannelFilterChain(new HeadFilter(), new TailFilter());
		filterChainBuilder.buildFilterChain(profile, filterChain);
	}
	
	protected void setState(State state) {
		this.state = state;
		this.state.checkCondition();
	}
	
	// --> replies to incoming messages <--
	
	protected boolean hasReply(int messageNumber) {
		return replies.containsKey(messageNumber);
	}
	
	protected Reply getReply(int messageNumber) {
		return replies.get(messageNumber);
	}
	
	protected Reply createReply(InternalSession session, Frame frame) {
		incrementOpenIncomingReplies();
		int messageNumber = frame.getMessageNumber();
		Reply reply = wrapReply(new DefaultReply(session, channelNumber, messageNumber));
		registerReply(messageNumber, reply);
		return reply;
	}
	
	protected void registerReply(int messageNumber, Reply reply) {
		if (replies.put(messageNumber, reply) != null) {
			throw new ProtocolException("there is already a reply registered for " 
					+ messageNumber
					+ " on channel " + channelNumber);
		}
	}
	
	protected void replyCompleted(int channelNumber, int messageNumber) {
		Reply reply = replies.remove(messageNumber);
		if (reply == null) {
			throw new IllegalStateException(
					"completed reply that does no longer exist (channel="
					+ channelNumber + ",message=" + messageNumber + ")");
		}
	}
	
	// --> replies to outgoing messages <--
	
	/**
	 * Gets the next ReplyHandlerHolder. The <var>messageNumber</var>
	 * must match the message number of the returned ReplyHandlerHolder. If that
	 * is not the case a protocol exception is thrown and the session is
	 * terminated. If the frame is not an intermediate frame, the frame
	 * handler is removed.
	 * 
	 * @param frame the frame for which the ReplyHandlerHolder is retrieved
	 * @return the ReplyHandlerHolder for the reply
	 */
	private ReplyHandlerHolder getReplyHandlerHolder(Frame frame) {
		synchronized (replyHandlerHolders) {
			ReplyHandlerHolder holder = replyHandlerHolders.getFirst();
			
			int messageNumber = frame.getMessageNumber();
			if (messageNumber != holder.getMessageNumber()) {
				throw new ProtocolException("next expected reply "
						+ " on channel " + frame.getChannelNumber()
						+ " has message number " 
						+ holder.getMessageNumber()
						+ "; received reply had message number " + messageNumber);
			}
			
			return holder;
		}
	}
	
	/**
	 * Registers a ReplyHandlerHolder. A ReplyHandlerHolder represents a reply that
	 * must be received later.
	 * 
	 * @param messageNumber the message number of the incoming reply
	 * @param handler the ReplyHandler that will process the reply
	 */
	protected void registerReplyHandler(final int messageNumber, final ReplyHandler handler) {
		synchronized (replyHandlerHolders) {
			replyHandlerHolders.addLast(new ReplyHandlerHolder(handler, messageNumber));
		}
	}
	
	// --> start of InternalChannel methods <--
	
	public void channelOpened(ChannelHandler channelHandler) {
		Assert.notNull("channelHandler", wrapChannelHandler(channelHandler));
		this.channelHandler = wrapChannelHandler(channelHandler);
		this.channelHandler.channelOpened(this);
	}

	private ChannelHandler wrapChannelHandler(ChannelHandler channelHandler) {
		return new FilterChannelHandler(filterChain, channelHandler);
	}
	
	public void receiveMSG(Frame frame) {
		Assert.holdsLock("session", sessionLock);
		
		Reply reply; 
		if (hasReply(frame.getMessageNumber())) {
			reply = getReply(frame.getMessageNumber());
		} else {
			reply = createReply(session, frame);
		}
		
		state.receiveMSG(frame, reply);
	}
	
	public void receiveRPY(Frame frame) {
		Assert.holdsLock("session", sessionLock);
		ReplyHandlerHolder holder = getReplyHandlerHolder(frame);

		try {
			state.receiveRPY(holder, frame);
		} finally {
			if (!frame.isIntermediate()) {
				outgoingReplyCompleted();
			}
		}
	}
	
	public void receiveERR(Frame frame) {
		Assert.holdsLock("session", sessionLock);
		ReplyHandlerHolder holder = getReplyHandlerHolder(frame);
		
		try {
			state.receiveERR(holder, frame);
		} finally {
			if (!frame.isIntermediate()) {
				outgoingReplyCompleted();
			}			
		}
	}
	
	public void receiveANS(Frame frame) {
		Assert.holdsLock("session", sessionLock);
		ReplyHandlerHolder holder = getReplyHandlerHolder(frame);
		state.receiveANS(holder, frame);
	}
	
	public void receiveNUL(Frame frame) {
		Assert.holdsLock("session", sessionLock);
		ReplyHandlerHolder holder = getReplyHandlerHolder(frame);
		
		try {
			state.receiveNUL(holder, frame);
		} finally {
			outgoingReplyCompleted();
		}
	}
	
	public boolean isAlive() {
		return state instanceof Alive;
	}
	
	public boolean isDead() {
		return state instanceof Dead;
	}
	
	public boolean isShuttingDown() {
		return !isAlive() && !isDead();
	}
	
	// --> end of InternalChannel methods <--
	
	// --> start of Channel methods <--
	
	public String getProfile() {
		return profile;
	}

	public Session getSession() {
		return session;
	}
	
	public MessageBuilder createMessageBuilder() {
		return new DefaultMessageBuilder();
	}
	
	public void sendMessage(Message message, ReplyHandler reply) {
		Assert.notNull("message", message);
		Assert.notNull("listener", reply);
		incrementOpenOutgoingReplies();
		state.sendMessage(message, wrapReplyHandler(reply));
	}
	
	private void lock() {
		if (sessionLock != null) {
			sessionLock.lock();
		}
	}
	
	private void unlock() {
		if (sessionLock != null) {
			sessionLock.unlock();
		}
	}
	
	private void doSendMessage(Message message, ReplyHandler replyHandler) {
		lock();
		try {
			int messageNumber = messageNumberSequence.next();
			registerReplyHandler(messageNumber, replyHandler);
			session.sendMSG(channelNumber, messageNumber, message, replyHandler);
		} finally {
			unlock();
		}
	}

	/*
	 * The passed in ReplyHandler is decorated by the the following 
	 * decorators:
	 * 
	 * 1. UnlockingReplyHandler: unlock / lock session lock
	 * 2. FilterReplyHandler:    passes request through filters
	 * 3. target:                after the filters are processed, this method is called
	 */
	protected ReplyHandler wrapReplyHandler(ReplyHandler replyHandler) {
		replyHandler = new FilterReplyHandler(filterChain, replyHandler);
		replyHandler = new UnlockingReplyHandler(replyHandler, sessionLock);
		return replyHandler;
	}
	
	public void close(CloseChannelCallback callback) {
		Assert.notNull("callback", callback);
		filterChain.fireFilterClose(callback);
	}

	protected Reply wrapReply(Reply reply) {
		reply = new ReplyWrapper(reply);
		reply = new LockingReply(reply, sessionLock);
		return new FilterReply(filterChain, reply);
	}
	
	public void channelCloseRequested(CloseCallback callback) {
		state.closeRequested(callback);
	}
	
	// --> end of Channel methods <--
	
	protected synchronized void incrementOpenOutgoingReplies() {
		openOutgoingReplies++;
	}
	
	protected void outgoingReplyCompleted() {
		synchronized (this) {
			openOutgoingReplies--;
			replyHandlerHolders.removeFirst();
		}
		state.checkCondition();
	}
	
	protected synchronized boolean hasOpenOutgoingReplies() {
		return openOutgoingReplies > 0;
	}
	
	protected synchronized void incrementOpenIncomingReplies() {
		openIncomingReplies++;
	}
	
	protected void incomingReplyCompleted() {
		synchronized (this) {
			openIncomingReplies--;
		}
		
		lock();
		try {
			state.checkCondition();
		} finally {
			unlock();
		}
	}
	
	protected synchronized boolean hasOpenIncomingReplies() {
		return openIncomingReplies > 0;
	}
	
	protected synchronized boolean isReadyToShutdown() {
		return !hasOpenIncomingReplies() && !hasOpenOutgoingReplies();
	}
	
	@Override
	public String toString() {
		return "channel-" + channelNumber;
	}

	/**
	 * Filter used by the {@link DefaultChannelFilterChain} at the head of
	 * the chain. Depending on the kind of operation either delegates
	 * to the next filter (incoming operations on {@link ChannelHandler} and
	 * {@link ReplyHandler}) or performs the requested operation (on outgoing
	 * operations, {@link Channel} and {@link Reply}).
	 */
	private final class HeadFilter extends ChannelFilterAdapter {
		@Override
		public void filterSendMessage(NextFilter next, Message message, ReplyHandler replyHandler) {
			doSendMessage(message, replyHandler);
		}
		
		@Override
		public void filterClose(NextFilter next, CloseChannelCallback callback) {
			state.closeInitiated(new UnlockingCloseChannelCallback(callback, sessionLock));
		}

		@Override
		public void filterSendRPY(NextFilter next, Message message) {
			FilterChainTargetHolder.getReply().sendRPY(message);
		}

		@Override
		public void filterSendERR(NextFilter next, Message message) {
			FilterChainTargetHolder.getReply().sendERR(message);
		}

		@Override
		public void filterSendANS(NextFilter next, Message message) {
			FilterChainTargetHolder.getReply().sendANS(message);
		}
		
		@Override
		public void filterSendNUL(NextFilter next) {
			FilterChainTargetHolder.getReply().sendNUL();
		}
	}

	/**
	 * Filter used by the {@link DefaultChannelFilterChain} at the tail of
	 * the chain. Depending on the kind of operation either delegates
	 * to the next filter (outgoing operations on {@link Channel} and
	 * {@link Reply}) or performs the requested operation (on incoming
	 * operations, {@link ChannelHandler} and {@link ReplyHandler}).
	 */
	private final class TailFilter extends ChannelFilterAdapter {
		@Override
		public void filterChannelOpened(NextFilter next, Channel channel) {
			FilterChainTargetHolder.getChannelHandler().channelOpened(ChannelImpl.this);
		}

		@Override
		public void filterMessageReceived(NextFilter next, Object message, Reply reply) {
			FilterChainTargetHolder.getChannelHandler().messageReceived(message, reply);
		}
		
		@Override
		public void filterChannelCloseRequested(NextFilter next, CloseChannelRequest r) {
			DefaultCloseChannelRequest request = (DefaultCloseChannelRequest) r;
			FilterChainTargetHolder.getChannelHandler().channelCloseRequested(request);
			
			CloseCallback callback = FilterChainTargetHolder.getCloseCallback();
			
			// TODO: request might not get here if filters stop before
			if (request.isAccepted()) {
				setState(new CloseRequested(callback));
			} else {
				callback.closeDeclined(550, "still working");
				setState(new Alive());
			}
		}
		
		@Override
		public void filterChannelClosed(NextFilter next) {
			FilterChainTargetHolder.getChannelHandler().channelClosed();
			setState(new Dead());
		}

		@Override
		public void filterReceivedRPY(NextFilter next, Object message) {
			FilterChainTargetHolder.getReplyHandler().receivedRPY(message);
		}
		
		@Override
		public void filterReceivedERR(NextFilter next, Object message) {
			FilterChainTargetHolder.getReplyHandler().receivedERR(message);
		}
		
		@Override
		public void filterReceivedANS(NextFilter next, Object message) {
			FilterChainTargetHolder.getReplyHandler().receivedANS(message);
		}
		
		@Override
		public void filterReceivedNUL(NextFilter next) {
			FilterChainTargetHolder.getReplyHandler().receivedNUL();
		}
	}
	
	/*
	 * The ReplyWrapper is used to count outstanding replies. This information
	 * is needed to know when a channel close can be accepted.
	 */
	private class ReplyWrapper implements Reply {
		
		private final Reply target;
		
		private ReplyWrapper(Reply target) {
			Assert.notNull("target", target);
			this.target = target;
		}
		
		public void sendANS(Message message) {
			target.sendANS(message);			
		}
		
		public void sendNUL() {
			incomingReplyCompleted();
			target.sendNUL();
		}
		
		public void sendERR(Message message) {
			incomingReplyCompleted();
			target.sendERR(message);
		}
		
		public void sendRPY(Message message) {
			incomingReplyCompleted();
			target.sendRPY(message);
		}
	}
	
	private static class ReplyHandlerHolder implements ReplyHandler {
		private final ReplyHandler target;
		private final int messageNumber;
		
		private ReplyHandlerHolder(ReplyHandler target, int messageNumber) {
			this.target = target;
			this.messageNumber = messageNumber;
		}
		
		int getMessageNumber() {
			return messageNumber;
		}
		
		public void receivedANS(Object message) {
			target.receivedANS(message);
		}
		
		public void receivedNUL() {
			target.receivedNUL();
		}
		
		public void receivedERR(Object message) {
			target.receivedERR(message);
		}
		
		public void receivedRPY(Object message) {
			target.receivedRPY(message);
		}
	}
	
	private static interface State {
		
		void checkCondition();
		
		void sendMessage(Message message, ReplyHandler replyHandler);
		
		void closeInitiated(CloseChannelCallback callback);
		
		void closeRequested(CloseCallback callback);
		
		void receiveMSG(Frame frame, Reply reply);
		
		void receiveRPY(ReplyHandler replyHandler, Frame frame);
		
		void receiveERR(ReplyHandler replyHandler, Frame frame);
		
		void receiveANS(ReplyHandler replyHandler, Frame frame);
		
		void receiveNUL(ReplyHandler replyHandler, Frame frame);
		
	}
	
	private abstract class AbstractState implements State {
		
		public void checkCondition() {
			// nothing to check
		}
		
		public void sendMessage(Message message, ReplyHandler replyHandler) {
			throw new IllegalStateException(buildExceptionMessage("sendMessage"));
		}
		
		public void closeInitiated(CloseChannelCallback callback) {
			throw new IllegalStateException(buildExceptionMessage("closeInitiated"));
		}
		
		public void closeRequested(CloseCallback callback) {
			throw new IllegalStateException(buildExceptionMessage("closeRequested"));
		}
		
		public void receiveMSG(Frame frame, Reply reply) {
			throw new IllegalStateException(buildExceptionMessage("receiveMSG"));
		}
		
		public void receiveRPY(ReplyHandler replyHandler, Frame frame) {
			throw new IllegalStateException(buildExceptionMessage("receiveRPY"));
		}
		
		public void receiveERR(ReplyHandler replyHandler, Frame frame) {
			throw new IllegalStateException(buildExceptionMessage("receiveERR"));
		}
		
		public void receiveANS(ReplyHandler replyHandler, Frame frame) {
			throw new IllegalStateException(buildExceptionMessage("receiveANS"));
		}
		
		public void receiveNUL(ReplyHandler replyHandler, Frame frame) {
			throw new IllegalStateException(buildExceptionMessage("receiveNUL"));
		}
		
		private String buildExceptionMessage(String method) {
			StringBuilder builder = new StringBuilder();
			builder.append("state ").append(getClass().getSimpleName());
			builder.append(" does not support ").append(method);
			builder.append(" (channel = ").append(channelNumber).append(")");
			return builder.toString();
		}
	}
	
	private abstract class AbstractReceivingState extends AbstractState {
		
		@Override
		public void receiveRPY(ReplyHandler replyHandler, Frame frame) {
			replyHandler.receivedRPY(frame);
		}
		
		@Override
		public void receiveERR(ReplyHandler replyHandler, Frame frame) {
			replyHandler.receivedERR(frame);
		}
		
		@Override
		public void receiveANS(ReplyHandler replyHandler, Frame frame) {
			replyHandler.receivedANS(frame);
		}
		
		@Override
		public void receiveNUL(ReplyHandler replyHandler, Frame frame) {
			replyHandler.receivedNUL();
		}
		
	}
	
	private class Alive extends AbstractReceivingState {
		
		@Override
		public void sendMessage(final Message message, final ReplyHandler replyHandler) {
			filterChain.fireFilterSendMessage(message, replyHandler);
		}
		
		@Override
		public void receiveMSG(Frame frame, Reply reply) {
			channelHandler.messageReceived(frame, reply);
		}
		
		@Override
		public void closeInitiated(CloseChannelCallback callback) {
			setState(new CloseInitiated(callback));
		}
		
		@Override
		public void closeRequested(CloseCallback callback) {
			FilterChainTargetHolder.setCloseCallback(callback);
			try {
				channelHandler.channelCloseRequested(new DefaultCloseChannelRequest());
			} finally {
				FilterChainTargetHolder.setCloseCallback(null);
			}
//			setState(new CloseRequested(callback));
		}
		
	}
	
	private class CloseInitiated extends AbstractReceivingState {
		
		private final CloseChannelCallback callback;
		
		private CloseInitiated(CloseChannelCallback callback) {
			this.callback = callback;
		}
		
		@Override
		public void receiveMSG(Frame frame, Reply reply) {
			channelHandler.messageReceived(frame, reply);
		}
		
		/**
		 * Sending the close channel request is allowed as soon as all
		 * sent messages have been acknowledged. beep4j is a bit more
		 * strict. I requires that for all sent messages complete
		 * replies have been received and that all received messages
		 * have been answered.
		 */
		@Override
		public void checkCondition() {
			if (isReadyToShutdown()) {
				final CloseCallback closeCallback = new CloseCallback() {
					public void closeDeclined(int code, String message) {
						callback.closeDeclined(code, message);
						setState(new Alive());
					}
					public void closeAccepted() {
						callback.closeAccepted();
						channelHandler.channelClosed();
					}
				};
				setState(new CloseInitiatedSentState(callback));
				session.requestChannelClose(channelNumber, closeCallback);
			}
		}
		
		/*
		 * If we receive a close request in this state, we accept the close
		 * request immediately without consulting the application. The
		 * reasoning is that the application already requested to close
		 * the channel, so it makes no sense to let it change that 
		 * decision.
		 */
		@Override
		public void closeRequested(CloseCallback closeCallback) {
			callback.closeAccepted();
			channelHandler.channelClosed();
			closeCallback.closeAccepted();
		}
		
	}
	
	private class CloseInitiatedSentState extends CloseInitiated {
		public CloseInitiatedSentState(CloseChannelCallback callback) {
			super(callback);
		}
		@Override
		public void checkCondition() {
			// nothing to check
		}
	}
	
	private class CloseRequested extends AbstractReceivingState {
		
		private final CloseCallback callback;
		
		private CloseRequested(CloseCallback callback) {
			this.callback = callback;
		}
		
		@Override
		public void receiveMSG(Frame frame, Reply handler) {
			throw new ProtocolException("the remote peer is not allowed to send "
					+ "further messages on a channel after sending a channel close request");
		}
		
		@Override
		public void checkCondition() {
			if (isReadyToShutdown()) {
				channelHandler.channelClosed();
				callback.closeAccepted();
			}
		}
	}
	
	private class Dead extends AbstractState {
		// dead is dead ;)
	}
	
	
	protected class DefaultReply implements Reply {
		
		private final InternalSession session;
		
		private final int channel;
		
		private final int messageNumber;
		
		private int answerNumber = 0;
		
		private boolean complete;
		
		public DefaultReply(InternalSession session, int channel, int messageNumber) {
			Assert.notNull("session", session);
			this.session = session;
			this.channel = channel;
			this.messageNumber = messageNumber;
		}
		
		private void checkCompletion() {
			if (complete) {
				throw new IllegalStateException("a complete reply has already been sent");
			}
		}

		private void complete() {
			complete = true;
			replyCompleted(channel, messageNumber);
		}
		
		public MessageBuilder createMessageBuilder() {
			return new DefaultMessageBuilder();
		}

		public void sendANS(Message message) {
			Assert.notNull("message", message);
			checkCompletion();
			session.sendANS(channel, messageNumber, answerNumber++, message);
		}
		
		public void sendERR(Message message) {
			Assert.notNull("message", message);
			checkCompletion();
			session.sendERR(channel, messageNumber, message);
			complete();
		}
		
		public void sendNUL() {
			checkCompletion();
			session.sendNUL(channel, messageNumber);
			complete();
		}
		
		public void sendRPY(Message message) {
			Assert.notNull("message", message);
			checkCompletion();
			session.sendRPY(channel, messageNumber, message);
			complete();
		}
		
	}

}
