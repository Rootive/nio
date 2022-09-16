package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;

public class Literal implements Actor {
    private final ByteBuffer data;

    public Literal(Object obj) throws JsonProcessingException {
        data = Util.single(new ObjectMapper().writeValueAsBytes(obj), Type.Literal);
    }

    @Override
    public ByteBuffer getData() {
        return data.duplicate();
    }
}
