/**
 * This file is part of CLaJ. The system that allows you to play with your friends, 
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025  Xpdustry
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.server;

import arc.Events;
import arc.net.Connection;
import arc.net.DcReason;

import com.xpdustry.claj.common.*;
import com.xpdustry.claj.common.status.CloseReason;
import com.xpdustry.claj.common.status.MessageType;


public class ClajEvents {
  /** Fire an event to the {@link ClajVars#loop}. Do nothing if {@link ClajVars#loop} is {@code null} or not started. */
  public static <T> void fire(T type) {
    ClajVars.loop.postSafe(() -> Events.fire(type));
  }
  /** Fire an event to the {@link ClajVars#loop}. */
  public static <T> void fire(Class<?> ctype, T type) {
    ClajVars.loop.postSafe(() -> Events.fire(ctype, type));
  }
  
  
  public static class ServerLoadedEvent {}
  public static class ServerStoppingEvent {}
  
  public static class ClientConnectedEvent {
    public final Connection connection;
    
    public ClientConnectedEvent(Connection connection) {
      this.connection = connection;
    }
  }
  /** @apiNote this event comes after {@link RoomClosedEvent} if the connection was the room host. */
  public static class ClientDisonnectedEvent {
    public final Connection connection;
    public final DcReason reason;
    /** not {@code null} if the client was in a room */
    public final ClajRoom room;
    
    public ClientDisonnectedEvent(Connection connection, DcReason reason, ClajRoom room) {
      this.connection = connection;
      this.reason = reason;
      this.room = room;
    }
  }
  
  /** Currently the only reason is for packet spam. */
  public static class ClientKickedEvent {
    public final Connection connection;
    
    public ClientKickedEvent(Connection connection) {
      this.connection = connection;
    }
  }
  /** When a connection join a room */
  public static class ConnectionJoinedEvent {
    public final Connection connection;
    public final ClajRoom room;
    
    public ConnectionJoinedEvent(Connection connection, ClajRoom room) {
      this.connection = connection;
      this.room = room;
    }
  }
  
  public static class RoomCreatedEvent {
    public final ClajRoom room;
    
    public RoomCreatedEvent(ClajRoom room) {
      this.room = room;
    }
  }
  public static class RoomClosedEvent {
    /** @apiNote the room is closed, so it cannot be used anymore. */
    public final ClajRoom room;
    
    public RoomClosedEvent(ClajRoom room) {
      this.room = room;
    }
  }
  public static class RoomCreationRejectedEvent {
    /** the connection that tried to create the room */
    public final Connection connection;
    public final CloseReason reason;
    
    public RoomCreationRejectedEvent(Connection connection, CloseReason reason) {
      this.connection = connection;
      this.reason = reason;
    }
  }
  
  /** 
   * Defines an action tried by a connection but was not allowed to do it.
   * <p>
   * E.g. a client of the room tried to close it, or the host tried to join another room while hosting one.
   */
  public static class ActionDeniedEvent {
    public final Connection connection;
    public final MessageType reason;
    
    public ActionDeniedEvent(Connection connection, MessageType reason) {
      this.connection = connection;
      this.reason = reason;
    }
  }
}
