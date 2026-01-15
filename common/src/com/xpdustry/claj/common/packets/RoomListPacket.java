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

import java.nio.ByteBuffer;
import java.util.BitSet;

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.net.stream.StreamSender;
import com.xpdustry.claj.common.status.GameState;


/** Can be a huge packet, should be sent with {@link StreamSender} instead. */
public class RoomListPacket /*extends DelayedPacket*/ implements Packet {
  public int size;
  public long[] rooms;
  public boolean[] isProtected;
  public GameState[] states;

  @Override
  public void read(ByteBufferInput read) {
    size = read.readInt();
    rooms = new long[size];
    isProtected = new boolean[size];
    states = new GameState[size];
    
    int length = ((size << 1) + Byte.SIZE - 1) / Byte.SIZE;
    BitSet bits = BitSet.valueOf((ByteBuffer)read.buffer.slice().limit(length));
    read.skipBytes(length);

    for (int i=0; i<size; i++) {
      isProtected[i] = bits.get(i << 1);
      rooms[i] = read.readLong();
      if (bits.get((i << 1) + 1)) 
        states[i] = RoomInfoPacket.readState(read);
    }
  }
  
  @Override
  public void write(ByteBufferOutput write) {
    write.writeInt(size);
    
    BitSet bits = new BitSet(size << 1);
    for (int i=0; i<size; i++) {
      if (isProtected[i]) bits.set(i << 1);
      if (states[i] != null) bits.set((i << 1) + 1);
    }
    
    int length = ((size << 1) + Byte.SIZE - 1) / Byte.SIZE;
    byte[] bytes = bits.toByteArray();
    write.write(bytes);
    for (int i=bytes.length; i<length; i++) write.writeByte(0);
    
    for (int i=0; i<size; i++) {
      write.writeLong(rooms[i]);
      if (states[i] != null) 
        RoomInfoPacket.writeState(write, states[i]);
    }
  }
}
