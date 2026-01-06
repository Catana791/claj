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

import arc.struct.ArrayMap;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


public class RoomListPacket extends DelayedPacket {
  /** Public rooms on the server and whether they are protected or not. */
  public ArrayMap<Long, Boolean> rooms;
  /** 
   * Because a server can hold many rooms, 
   * putting all their id in a single packet can leads to a buffer overflow. <br>
   * This field control whether more room ids are waiting. <br>
   * Commonly {@link #rooms} are given per {@code 1024}.
   */
  public boolean hasNext;
  
  @Override
  protected void readImpl(ByteBufferInput read) {
    hasNext = read.readBoolean();
    rooms = new ArrayMap<>(read.readShort());
    for (int i=0; i<rooms.size; i++) 
      rooms.put(read.readLong(), null); // protected is read after
    readPackedBits(read);
  }
  
  @Override
  public void write(ByteBufferOutput write) {
    write.writeBoolean(hasNext);
    write.writeShort(rooms.size);
    for (long id : rooms.keys) 
      write.writeLong(id);
    writePackedBits(write);
  }
  
  private void readPackedBits(ByteBufferInput read) {
    byte current = 0;
    int pos;
    for (int i=0; i<rooms.size; i++) {
      pos = i % Byte.SIZE;
      if (pos == 0) current = read.readByte();
      rooms.setValue(i, ((current >>> pos) & 1) != 0);
    }
  }
  
  private void writePackedBits(ByteBufferOutput write) {
    byte current = 0;
    int pos;
    for (int i=0; i<rooms.size; i++) {
      pos = i % Byte.SIZE;
      if (rooms.values[i]) current |= 1 << pos;
      if (pos == 7) {
        write.writeByte(current);
        current = 0;
      }
    }
    if (rooms.size % Byte.SIZE != 0) write.writeByte(current);    
  }
}
