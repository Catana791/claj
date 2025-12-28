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

package com.xpdustry.claj.common;

import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Prov;
import arc.net.ArcNetException;
import arc.net.Connection;
import arc.struct.ArrayMap;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;

import com.xpdustry.claj.common.packets.Packet;


public class ClajNet {
  public static final byte frameworkId = -2;
  /** Old CLaJ id. */
  public static final byte oldId = -3;
  /** Identifier for CLaJ packets. */
  public static final byte id = -4;
  
  protected static final ArrayMap<Class<?>, Prov<? extends Packet>> packets = new ArrayMap<>();
  protected static final ObjectIntMap<Class<?>> packetToId = new ObjectIntMap<>();
  
  protected static final ObjectMap<Class<?>, Cons<? extends Packet>> clientListeners = new ObjectMap<>();
  protected static final ObjectMap<Class<?>, Cons2<Connection, ? extends Packet>> serverListeners = new ObjectMap<>();
  
  static {
    ClajPackets.register();
  }
  
  /** Registers a new packet type for serialization. */
  public static <T extends Packet> void register(Prov<T> cons) {
    Class<?> type = cons.get().getClass();
    if (packetToId.containsKey(type)) return;
    packetToId.put(type, packets.size);
    packets.put(type, cons);
  }

  public static byte getId(Packet packet) {
    int id = packetToId.get(packet.getClass());
    if(id == -1) throw new ArcNetException("Unknown packet type: " + packet.getClass());
    return (byte)id;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Packet> T newPacket(byte id) {
    if (id < 0 || id >= packets.size) throw new ArcNetException("Unknown packet id: " + id);
    return ((Prov<T>)packets.getValueAt(id)).get();
  }
  
  
  /** Registers a client listener for when an object is received. */
  public <T extends Packet> void handleClient(Class<T> type, Cons<T> listener) { 
    clientListeners.put(type, listener); 
  }

  /** Registers a server listener for when an object is received. */
  public <T extends Packet> void handleServer(Class<T> type, Cons2<Connection, T> listener) {
    serverListeners.put(type, listener);
  }
  
  /** Call to handle a packet being received for the client. */
  @SuppressWarnings("unchecked")
  public void handleClientReceived(Packet object) {
    object.handled();

    var listener = (Cons<Packet>)clientListeners.get(object.getClass());
    if (listener != null) listener.get(object);
    else object.handleClient();
  }
  
  /** Call to handle a packet being received for the server. */
  @SuppressWarnings("unchecked")
  public void handleServerReceived(Connection connection, Packet object) {
    if (!connection.isConnected()) return;
    object.handled();

    //handle object normally
    var listener = (Cons2<Connection, Packet>)serverListeners.get(object.getClass());
    if (listener != null) listener.get(connection, object);
    else object.handleServer(connection);
  }         
}