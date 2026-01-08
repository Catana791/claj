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
import com.xpdustry.claj.common.ClajPackets.RawPacket;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.packets.ServerInfoPacket;


public class ClajSerializer implements NetSerializer, FrameworkSerializer {
    //maybe faster without ThreadLocal?
    protected final ByteBufferInput read = new ByteBufferInput();
    protected final ByteBufferOutput write = new ByteBufferOutput();

    @Override
    public Object read(ByteBuffer buffer) {
      // HAAA whyyy
      if (!buffer.hasRemaining()) {
        read.buffer = buffer;
        // Probably a CLaJ discovery reply from an old server...
        return new ServerInfoPacket().r(read);
      } 
      
      switch (buffer.get()) {
        case ClajNet.frameworkId: 
          return readFramework(buffer);
        
        case ClajNet.oldId:
          throw new ArcNetException("Received a packet from the old CLaJ protocol");
          
        case ClajNet.id:
          read.buffer = buffer;
          Packet packet = ClajNet.newPacket(buffer.get());
          packet.read(read);
          return packet;
          
        default:
          buffer.position(buffer.position()-1);
          throw new ArcNetException("Unknown protocol type: " + buffer.get());
      }
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ByteBuffer buf) {
        buffer.put(buf);
        
      } else if (object instanceof FrameworkMessage framework) {
        buffer.put(ClajNet.frameworkId);
        writeFramework(buffer, framework);

      } else if (object instanceof Packet packet) {
        write.buffer = buffer;
        if (!(object instanceof RawPacket)) 
          buffer.put(ClajNet.id).put(ClajNet.getId(packet));
        packet.write(write);
        
      } else {
        throw new ArcNetException("Unknown packet type: " + object.getClass());
      }
    }
  }
