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


/** Can be a huge packet, should be sent with {@link StreamSender} instead. */
public class RoomListPacket extends DelayedPacket {
  public int size;
  public long[] rooms;
  public boolean[] isProtected;
  public ByteBuffer[] states;

  @Override
  protected void readImpl(ByteBufferInput read) {
    init(read.readInt());

    int length = ceilDiv(size, Byte.SIZE);
    BitSet bits = BitSet.valueOf((ByteBuffer)read.buffer.slice().limit(length));
    read.skipBytes(length);

    for (int i=0; i<size; i++) {
      isProtected[i] = bits.get(i);
      rooms[i] = read.readLong();
      byte[] data = new byte[read.readChar()];
      read.readFully(data);
      states[i] = ByteBuffer.wrap(data);
    }
  }

  @Override
  public void write(ByteBufferOutput write) {
    write.writeInt(size);

    BitSet bits = new BitSet(size);
    for (int i=0; i<size; i++) {
      if (isProtected[i]) bits.set(i);
    }

    int length = ceilDiv(size, Byte.SIZE);
    byte[] bytes = bits.toByteArray();
    write.write(bytes);
    for (int i=bytes.length; i<length; i++) write.writeByte(0);

    for (int i=0; i<size; i++) {
      write.writeLong(rooms[i]);
      ByteBuffer state = states[i];
      if (state != null) {
        write.writeChar(state.remaining());
        write.buffer.put(state);
      } else write.writeChar(0);
    }
  }

  public void init(int size) {
    this.size = size;
    rooms = new long[size];
    isProtected = new boolean[size];
    states = new ByteBuffer[size];
  }

  private static int ceilDiv(int a, int b) {
    return (a + b - 1) / b;
  }
}
