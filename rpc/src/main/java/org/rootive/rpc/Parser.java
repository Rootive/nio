package org.rootive.rpc;

import java.util.ArrayList;

public class Parser {
    public enum Type {
        Unknown, Literal, Reference, Functor
    }
    private final Type type;
    private String literal;
    private Signature signature;
    private ArrayList<Parser> parameters;

    private int isLeftBracket(char ch) {
        if (ch == '{') {
            return 3;
        } else if (ch == '[') {
            return 2;
        } else if (ch == '(') {
            return 1;
        } else {
            return 0;
        }
    }
    private int isRightBracket(char ch) {
        if (ch == '}') {
            return 3;
        } else if (ch == ']') {
            return 2;
        } else if (ch == ')') {
            return 1;
        } else {
            return 0;
        }
    }
    private int isBracket(char ch) {
        return isLeftBracket(ch) + isRightBracket(ch);
    }
    public Parser(String string) {
        int rbrac = 0;
        var end = string.length() - 1;
        if (string.charAt(0) == '@') {
            var lbrac = string.indexOf('(');
            if (lbrac == -1) {
                type = Type.Reference;
            } else {
                rbrac = string.indexOf(')', lbrac);
                if (rbrac == -1) {
                    type = Type.Unknown;
                } else {
                    if (end == rbrac) {
                        type = Type.Reference;
                    } else if (string.charAt(rbrac + 1) == '(' && string.charAt(end) == ')') {
                        type = Type.Functor;
                    } else {
                        literal = string;
                        type = Type.Unknown;
                    }
                }
            }
        } else {
            type = Type.Literal;
            literal = string;
        }

        if (type == Type.Reference) {
            signature = new Signature(string.substring(2));
        } else if (type == Type.Functor) {
            parameters = new ArrayList<>();
            var last = rbrac + 1;
            signature = new Signature(string.substring(2, last));

            int bracket = 0;
            int type = 0;
            for (int _i = last + 1; _i < end; ++_i) {
                var ch = string.charAt(_i);
                if (type != 4) {
                    var _bracketType = isBracket(ch);
                    if (_bracketType > 0) {
                        if (isLeftBracket(ch) > 0) {
                            if (bracket == 0) {
                                type = _bracketType;
                                ++bracket;
                            } else if (type == _bracketType) {
                                ++bracket;
                            }
                        } else if (type == _bracketType) {
                            --bracket;
                        }
                    } else if (bracket == 0) {
                        if (ch == ',') {
                            parameters.add(new Parser(string.substring(last + 1, _i)));
                            last = _i;
                        } else if (ch == '"') {
                            type = 4;
                        }
                    }
                } else {
                    if (ch == '\\') {
                        ++_i;
                    } else if (ch == '"') {
                        type = 0;
                    }
                }
            }
            if (++last < end) {
                parameters.add(new Parser(string.substring(last, end)));
            }
            parameters.trimToSize();
        }
    }

    public Type getType() {
        return type;
    }

    public String getLiteral() {
        return literal;
    }

    public Signature getSignature() {
        return signature;
    }

    public ArrayList<Parser> getParameters() {
        return parameters;
    }
}
