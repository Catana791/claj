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

package com.xpdustry.claj.common.packets;

import arc.func.Cons2;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajPackets.RawPacket;


/** Special packet for connection packet wrapping. */
public class ConnectionPacketWrapPacket extends ConnectionWrapperPacket {
  /** Used to notify serializer to read the rest. */
  public static Cons2<ConnectionPacketWrapPacket, ByteBufferInput> readContent;
  /** Used to notify serializer to write the rest. */
  public static Cons2<ConnectionPacketWrapPacket, ByteBufferOutput> writeContent;
  
  /** Decoded object received by the client. Should be handled by the serializer. */
  public Object object;
  /** Copy of the raw packet received by the server. Should be handled by the serializer. */
  public RawPacket raw;
  
  public boolean isTCP;
  
  protected void read0(ByteBufferInput read) {
    isTCP = read.readBoolean();
    if (readContent != null) readContent.get(this, read);
  }
  
  protected void write0(ByteBufferOutput write) {
    write.writeBoolean(isTCP);
    if (writeContent != null) writeContent.get(this, write);
  }
}
