/**
 * This file is part of CLaJ. The system that allows you to play with your friends, 
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025-2026  Xpdustry
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

package com.xpdustry.claj.common.net;

import arc.Core;
import arc.func.Cons;
import arc.func.Cons2;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.EndPoint;
import arc.net.NetListener;
import arc.struct.ObjectMap;
import arc.util.Log;

import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.net.stream.StreamPacket;
import com.xpdustry.claj.common.net.stream.StreamReceiver;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.util.Strings;


/** A server listener delegating packet decoding and reception to the main app. */
public class ServerReceiver {
  protected final ObjectMap<Class<?>, Cons2<Connection, ?>> listeners = new ObjectMap<>();
  protected boolean delegated;
  
  public ServerReceiver(EndPoint server) { this(server, true); }
  public ServerReceiver(EndPoint server, boolean delegateReceive) {
    server.addListener(new NetListener() {
      @Override
      public void connected(Connection connection) { 
        Connect packet = new Connect();
        packet.address = Strings.getIP(connection);
        delegateReceive(connection, packet);
      }
      
      @Override
      public void disconnected(Connection connection, DcReason reason) { 
        Disconnect packet = new Disconnect();
        packet.reason = reason;
        delegateReceive(connection, packet);
      }
      
      @Override
      public void received(Connection connection, Object object) {
        if (!(object instanceof Packet packet)) return;
        delegateReceive(connection, packet);
      }
    });
  }
  
  /** Whether packet reception is delegated to the main thread or not. */
  public boolean delegated() {
    return delegated;
  }
  
  public <T extends Packet> void handle(Class<T> type, Runnable listener) { 
    handle(type, (c, p) -> listener.run()); 
  }
  
  public <T extends Packet> void handle(Class<T> type, Cons<Connection> listener) { 
    handle(type, (c, p) -> listener.get(c)); 
  }
  
  public <T extends Packet> void handle(Class<T> type, Cons2<Connection, T> listener) { 
    listeners.put(type, listener); 
  }
  
  @SuppressWarnings("unchecked")
  public <T extends Packet> Cons2<Connection, T> getListener(Class<T> type) { 
    return (Cons2<Connection, T>)listeners.get(type);
  }
  
  /** Send packet reception to the main thread or not according to {@link #delegated}. */
  public void delegateReceive(Connection connection, Packet packet) {
    if (delegated) Core.app.post(() -> received(connection, packet));
    else received(connection, packet);
  }
  
  @SuppressWarnings("unchecked")
  public void received(Connection connection, Packet packet) {
    try { 
      if (!connection.isConnected()) return;
      packet.handled();
      
      if (packet instanceof StreamPacket stream) {
        packet = StreamReceiver.received(connection, stream);
        if (packet != null) received(connection, packet);
        return;
      }
  
      var listener = (Cons2<Connection, Packet>)listeners.get(packet.getClass());
      if (listener != null) listener.get(connection, packet);
      else packet.handleServer(connection);
    } catch (Throwable e) { Log.err(e); }
  }
}   