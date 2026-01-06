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

import arc.func.Prov;
import arc.net.ArcNetException;
import arc.struct.ArrayMap;
import arc.struct.ObjectIntMap;

import com.xpdustry.claj.common.packets.Packet;


public class ClajNet {
  public static final byte frameworkId = -2;
  /** Old CLaJ id. */
  public static final byte oldId = -3;
  /** Identifier for CLaJ packets. */
  public static final byte id = -4;
  
  protected static final ArrayMap<Class<?>, Prov<? extends Packet>> packets = new ArrayMap<>();
  protected static final ObjectIntMap<Class<?>> packetToId = new ObjectIntMap<>();

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
}