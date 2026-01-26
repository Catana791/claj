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

package com.xpdustry.claj.api;

import java.nio.ByteBuffer;

import arc.net.ArcNetException;
import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.Packet;


public class ClajSerializer implements NetSerializer, FrameworkSerializer {
  //maybe faster without ThreadLocal?
  protected final ByteBufferInput read = new ByteBufferInput();
  protected final ByteBufferOutput write = new ByteBufferOutput();

  @Override
  public Object read(ByteBuffer buffer) {
    if (!buffer.hasRemaining()) return null;
    return switch (buffer.get()) {
      case ClajNet.frameworkId -> readFramework(buffer);
      case ClajNet.oldId -> throw new ArcNetException("Received a packet from the old CLaJ protocol");
      case ClajNet.id -> readClaj(buffer);
      default -> {
        buffer.position(buffer.position()-1);
        throw new ArcNetException("Unknown protocol type: " + buffer.get());
      }
    };
  }

  public Packet readClaj(ByteBuffer buffer) {
    Packet packet = ClajNet.newPacket(buffer.get());
    read.buffer = buffer;
    packet.read(read);
    return packet;
  }

  @Override
  public void write(ByteBuffer buffer, Object object) {
    if (object instanceof ByteBuffer buf) {
      buffer.put(buf);

    } else if (object instanceof FrameworkMessage framework) {
      buffer.put(ClajNet.frameworkId);
      writeFramework(buffer, framework);

    } else if (object instanceof Packet packet) {
      buffer.put(ClajNet.id).put(ClajNet.getId(packet));
      write.buffer = buffer;
      packet.write(write);

    } else {
      throw new ArcNetException("Unknown packet type: " + object.getClass());
    }
  }

  /*
  // Default methods sends a signed short instead of an unsigned one...
  @Override
  public void writeLength(ByteBuffer buffer, int length) {
    buffer.putChar((char)length); // char is encoded as an unsigned short
  }

  @Override
  public int readLength(ByteBuffer buffer) {
    return buffer.getChar();
  }
  */
}
