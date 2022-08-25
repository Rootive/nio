package org.rootive.gadget;

public class Linked<T> {
    private LinkedNode<T> h;
    private LinkedNode<T> t;

    public Linked() {}
    public Linked(LinkedNode<T> h, LinkedNode<T> t) {
        this.h = h;
        this.t = t;
    }
    public void addLast(T v) {
        var n = new LinkedNode<>(v);
        if (t != null) {
            t.link(n);
        } else {
            h = n;
        }
        t = n;
    }
    public T removeFirst() {
        var ret = h;
        h = h.right();
        ret.rSplit();
        return ret.v;
    }
    public boolean isEmpty() {
        return h == null;
    }
    public void clear() {
        h = t = null;
    }
    public LinkedNode<T> head() {
        return h;
    }
    public LinkedNode<T> tail() {
        return t;
    }
    public void link(Linked<T> a) {
        if (!a.isEmpty()) {
            if (t != null) {
                t.link(a.h);
            } else {
                h = a.h;
            }
            t = a.t;
        }
    }
    public Linked<T> lSplit(LinkedNode<T> n) {
        Linked<T> ret;
        if (h != n) {
            ret = new Linked<T>(h, n.left());
            h = n;
            n.lSplit();
        } else {
            ret = new Linked<>();
        }
        return ret;
    }
    public void split(LinkedNode<T> n) {
        if (n == h) {
            h = n.right();
        }
        if (n == t) {
            t = n.left();
        }
        n.split();
    }

}
