package net.sf.beep4j;

import java.nio.ByteBuffer;


public interface Frame {
	
	int getChannelNumber();
	
	int getMessageNumber();
	
	int getAnswerNumber();
	
	boolean isIntermediate();
	
	int getSize();
	
	ByteBuffer getByteBuffer();

	MessageType getType();
	
}
