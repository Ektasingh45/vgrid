package com.vgrid.ocpp;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket server that chargers connect to, exactly like an operator's central system (CSMS):
 *
 *     ws://<host>:<port>/ocpp/<chargePointId>      sub-protocol "ocpp1.6"
 *
 * The connection stays open; every message is passed to OcppHandler, and its reply is sent back.
 */
public class OcppServer extends WebSocketServer {
    private final OcppHandler handler;
    private final Consumer<String> log;
    private final Consumer<Exception> onFatalError;
    private final Map<String, WebSocket> chargers = new ConcurrentHashMap<>();

    public OcppServer(int port, OcppHandler handler, Consumer<String> log, Consumer<Exception> onFatalError) {
        // Chargers ask for the "ocpp1.6" sub-protocol; clients that send none are accepted too.
        super(new InetSocketAddress(port), List.of(new Draft_6455(Collections.emptyList(),
                List.of(new Protocol("ocpp1.6"), new Protocol("")))));
        this.handler = handler;
        this.log = log;
        this.onFatalError = onFatalError;
        setReuseAddr(true);
    }

    @Override
    public void onStart() {
        log.accept("OCPP server listening on ws://localhost:" + getPort() + "/ocpp/<chargePointId>");
        log.accept("Waiting for chargers. Close this window (or press Ctrl+C) to stop the server.");
    }

    /** A charger connects. Its id is the last part of the URL; unknown ids are refused. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String path = handshake.getResourceDescriptor();
        String id = path.substring(path.lastIndexOf('/') + 1);
        int query = id.indexOf('?');
        if (query >= 0) id = id.substring(0, query);
        if (id.isBlank() || !handler.isKnown(id)) {
            log.accept("REFUSED unknown charge point '" + id + "' (" + path + ")");
            conn.close(1008, "Unknown charge point id");
            return;
        }
        WebSocket previous = chargers.put(id, conn);
        if (previous != null && previous != conn) {
            previous.close(1000, "Replaced by a new connection");
        }
        conn.setAttachment(id);
        handler.connected(id);
        log.accept("CONNECTED " + id + " from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String id = conn.getAttachment();
        if (id == null) return;
        log.accept(id + "  ->  " + message);
        String reply = handler.handle(id, message);
        if (reply != null) {
            conn.send(reply);
            log.accept(id + "  <-  " + reply);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String id = conn.getAttachment();
        if (id != null && chargers.remove(id, conn)) {
            handler.disconnected(id);
            log.accept("DISCONNECTED " + id + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            onFatalError.accept(ex); // the server itself failed, e.g. the port is already in use
        } else {
            log.accept("ERROR on " + conn.getAttachment() + ": " + ex.getMessage());
        }
    }
}
