package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;

public class Literal implements Line {

    private final ByteBuffer byteBuffer;

    public Literal(Object object) throws JsonProcessingException {
        final byte[] bytes = new ObjectMapper().writeValueAsBytes(object);
        byteBuffer = Util.line(Type.Literal, ByteBuffer.wrap(bytes));
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return byteBuffer.duplicate();
    }
}
