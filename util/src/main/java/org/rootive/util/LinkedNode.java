package org.rootive.util;

import java.util.function.Predicate;

public class LinkedNode<T> {
    private LinkedNode<T> l;
    private LinkedNode<T> r;
    public T v;

    public LinkedNode(T v) {
        this.v = v;
    }
    public LinkedNode<T> left() {
        return l;
    }
    public LinkedNode<T> right() {
        return r;
    }
    void breakLeft() {
        if (l != null) {
            l.r = null;
            l = null;
        }
    }
    void breakRight() {
        if (r != null) {
            r.l = null;
            r = null;
        }
    }
    void escape() {
        if (l != null) {
            l.r = r;
        }
        if (r != null) {
            r.l = l;
        }
        l = r = null;
    }
    void linkRight(LinkedNode<T> n) {
        n.l = this;
        r = n;
    }
    public LinkedNode<T> find(Predicate<T> p) {
        var n = this;
        do {
            if (p.test(n.v)) {
                break;
            }
            n = n.right();
        } while (n != null);
        return n;
    }
}