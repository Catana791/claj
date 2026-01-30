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

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


public class RoomStatePacket extends RoomLinkPacket {
  public ByteBuffer state;

  @Override
  protected void readImpl(ByteBufferInput read) {
    super.readImpl(read);
    byte[] data = new byte[read.readChar()];
    read.readFully(data);
    state = ByteBuffer.wrap(data);
  }

  @Override
  public void write(ByteBufferOutput write) {
    super.write(write);
    write.writeChar(state.remaining());
    //write.write(state.array(), state.position(), state.remaining());
    write.buffer.put(state);
  }
}
