package org.rootive.dhd;

public class DHDClient {
    static public record Download(boolean bAssigned, DHTTPDownloader.Piece[] pieces) { }
}
