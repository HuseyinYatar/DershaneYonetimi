/**
 * Server-to-client push over WebSocket. Clients connect with a one-time ticket, the server tracks
 * who is online (only to decide whether a {@code NewMessage} event is needed) and fans frames out
 * to every pod through Redis Pub/Sub. All writes are ordinary REST calls; nothing is sent upstream
 * except heartbeats.
 */
@ApplicationModule(
    allowedDependencies = {"shared", "conversation::port-in", "conversation::port-out"})
package com.dershane.messagingservice.realtime;

import org.springframework.modulith.ApplicationModule;
