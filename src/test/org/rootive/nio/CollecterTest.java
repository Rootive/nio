package org.rootive.nio;

import org.junit.Test;
import org.rootive.rpc.Collecter;

import java.nio.ByteBuffer;

public class CollecterTest {
    static private void put(String s, ByteBuffer buffer) {
        buffer.put(s.getBytes());
        buffer.limit(buffer.position());
        buffer.position(0);
    }

    @Test
    public void collecterTest() {
        ByteBuffer one = ByteBuffer.allocate(64);
        ByteBuffer two = ByteBuffer.allocate(64);
        ByteBuffer three = ByteBuffer.allocate(64);
        ByteBuffer four = ByteBuffer.allocate(64);
        // oad.2(d{d((   d@.afeiud   iudhaui(das)a  sd(sda)123455
        put("oad.2(d{d((", one);
        put("d@.afeiud", two);
        put("iudhaui(das)a", three);
        put("sd(sda)123455", four);
        ByteBufferList buffers1 = new ByteBufferList();
        ByteBufferList buffers2 = new ByteBufferList();
        buffers1.addLast(one);
        buffers1.addLast(two);
        buffers2.addLast(three);
        buffers2.addLast(four);
        Collecter collecter = new Collecter();
        collecter.collect(buffers1);
        collecter.collect(buffers2);
        String res = collecter.toString();
        System.out.println(res);
        System.out.println(buffers1.totalRemaining() + " " + buffers2.totalRemaining());
        assert res.equals("@.afeiudiudhaui(das)asd(sda)");
        assert buffers1.totalRemaining() == 0;
        assert buffers2.totalRemaining() == 6;
    }
}
