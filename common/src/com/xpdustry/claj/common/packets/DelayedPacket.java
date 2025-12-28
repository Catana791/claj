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


/** 
 * Bufferize the data in {@link #READ} at {@link #read(ByteBufferInput)} call. <br>
 * Real reading will be done at {@link #handled()} call.
 */
public abstract class DelayedPacket implements Packet {
  private static final byte[] NODATA = {};
  private static final ByteBuffer EMPTYBUFF = ByteBuffer.wrap(NODATA);
  private static final ByteBufferInput READ = new ByteBufferInput(EMPTYBUFF);
  
  private byte[] DATA = NODATA;
  
  @Override
  public final void read(ByteBufferInput read) {
    DATA = new byte[read.buffer.remaining()];
    read.readFully(DATA);
  }  
  
  @Override
  public final void handled() {
    if (DATA == NODATA) return; // avoid double reading
    READ.buffer = ByteBuffer.wrap(DATA);
    readImpl(READ);
    DATA = NODATA;
    READ.buffer = EMPTYBUFF;
  }
  
  protected abstract void readImpl(ByteBufferInput read);
}
