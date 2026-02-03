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

package com.xpdustry.claj.server;

import java.nio.ByteBuffer;

import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class ClajServerSerializer implements NetSerializer, FrameworkSerializer {
  static {
    // Set wrapper serializer
    ConnectionPacketWrapPacket.serializer = new ConnectionPacketWrapPacket.Serializer() {
      @Override
      public void read(ConnectionPacketWrapPacket packet, ByteBufferInput read) {
        packet.raw = new RawPacket().r(read);
      }

      @Override
      public void write(ConnectionPacketWrapPacket packet, ByteBufferOutput write) {
        packet.raw.w(write);
      }
    };
  }

  protected final ThreadLocal<ByteBufferInput> read = Threads.local(ByteBufferInput::new);
  protected final ThreadLocal<ByteBufferOutput> write = Threads.local(ByteBufferOutput::new);
  protected final NetworkSpeed networkSpeed;
  private int lastPos;

  /** @param networkSpeed is for debugging, sets to null to disable it */
  public ClajServerSerializer(NetworkSpeed networkSpeed) {
    this.networkSpeed = networkSpeed;
  }

  @Override
  public Object read(ByteBuffer buffer) {
    if (networkSpeed != null) networkSpeed.downloadMark(buffer.remaining());
    ByteBufferInput readi = read.get();
    readi.buffer = buffer;

    return switch (buffer.get()) {
      case ClajNet.frameworkId -> readFramework(buffer);
      case ClajNet.oldId -> Strings.readUTF(readi);
      case ClajNet.id -> {
        Packet packet = ClajNet.newPacket(buffer.get());
        packet.read(readi);
        yield packet;
      }
      // Non-claj packets are saved as raw buffer, to avoid re-serialization
      default -> {
        buffer.position(buffer.position()-1);
        yield new RawPacket().r(readi);
      }
    };
  }

  @Override
  public void write(ByteBuffer buffer, Object object) {
    if (networkSpeed != null) lastPos = buffer.position();
    ByteBufferOutput writeo = write.get();
    writeo.buffer = buffer;

    if (object instanceof ByteBuffer buf) {
      buffer.put(buf);

    } else if (object instanceof FrameworkMessage framework) {
      buffer.put(ClajNet.frameworkId);
      writeFramework(buffer, framework);

    } else if (object instanceof String str && ClajConfig.warnDeprecated) {
      buffer.put(ClajNet.oldId);
      Strings.writeUTF(writeo, str);

    } else if (object instanceof Packet packet) {
      if (!(object instanceof RawPacket))
        buffer.put(ClajNet.id).put(ClajNet.getId(packet));
      packet.write(writeo);
    }

    if (networkSpeed != null) networkSpeed.uploadMark(buffer.position() - lastPos);
  }
}
