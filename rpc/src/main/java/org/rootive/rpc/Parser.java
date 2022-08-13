package org.rootive.rpc;

import java.util.ArrayList;

public class Parser {
    private final Signature signature;
    private final ArrayList<String> parameterStrings = new ArrayList<>();

    public Parser(String string) {
        var lbrac = string.lastIndexOf('(');
        signature = new Signature(string.substring(2, lbrac));
        String parametersString = string.substring(lbrac);

        int bracket = 0;
        boolean bBrace = false;
        int last = parametersString.indexOf('(');
        int r = parametersString.lastIndexOf(')');
        for (int l = last + 1; l < r; ++l) {
            var ch = parametersString.charAt(l);
            if (ch == '[' || ch == '{') {
                if (bracket == 0) {
                    bBrace = ch == '{';
                    ++bracket;
                } else {
                    if (bBrace == (ch == '{')) {
                        ++bracket;
                    }
                }
            } else if ((ch == ']' || ch == '}') && bBrace == (ch == '}')) {
                --bracket;
            } else if (ch == ',' && bracket == 0) {
                parameterStrings.add(parametersString.substring(last + 1, l));
                last = l;
            }
        }
        if (++last < r) {
            parameterStrings.add(parametersString.substring(last, r));
        }


    }

    public Signature getSignature() {
        return signature;
    }

    public ArrayList<String> getParameterStrings() {
        return parameterStrings;
    }
}
