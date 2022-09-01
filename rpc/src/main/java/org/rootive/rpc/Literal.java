package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;

public class Literal implements Actor {
    private ByteBuffer data;

    public Literal(Object obj) throws JsonProcessingException {
        var json = new ObjectMapper().writeValueAsBytes(obj);
        data = ByteBuffer.allocate(Constexpr.headerSize + json.length)
                .putInt(json.length)
                .put((byte) Type.Literal.ordinal())
                .put(json)
                .flip();
    }

    @Override
    public ByteBuffer getData() {
        return data.duplicate();
    }
}
