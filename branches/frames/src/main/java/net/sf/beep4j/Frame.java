package net.sf.beep4j;

import java.nio.ByteBuffer;

import net.sf.beep4j.internal.stream.MessageType;

public interface Frame {
	
	int getChannelNumber();
	
	int getMessageNumber();
	
	int getAnswerNumber();
	
	boolean isIntermediate();
	
	int getSize();
	
	ByteBuffer getByteBuffer();

	MessageType getType();
	
}
