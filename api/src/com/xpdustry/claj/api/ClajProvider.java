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

import java.util.concurrent.ExecutorService;

import arc.net.NetListener;

import com.xpdustry.claj.common.packets.ConnectionPacketWrapPacket.Serializer;
import com.xpdustry.claj.common.status.GameState;
import com.xpdustry.claj.common.status.MessageType;


/** Interface that provides implementation dependent client-side things. */
public interface ClajProvider {
  /** Executor used to post connecting tasks. If {@code null}, these operations will be blocking. */
  default ExecutorService getExecutor() { return null; }
  
  /** Used to create new proxy clients. Cannot be {@code null}. */
  default ClajProxy newProxy() { return new ClajProxy(this); };
  /** Used to create new pinger clients. Cannot be {@code null}. */
  default ClajPinger newPinger() { return new ClajPinger(); }
  
  /** Listener added to all virtual connections. Can be {@code null}. */
  default NetListener getConnectionListener() { return null; }
  /** The implementation's major version, used to request a room creation. Must be equals to the server. */
  int getVersion();
  /** The actual room state. Will be sent periodically to the server. Can be {@code null} to not provide state. */
  default GameState getRoomState(ClajProxy proxy) { return null; }
  /** 
   * Connect the client to the specified server. <br>
   * {@code success} can be null and must be called if connected successfully. 
   */
  default void connectClient(String host, int port, Runnable success) { if (success != null) success.run(); }
  
  /** 
   * <strong>Essential for the protocol to work!</strong>
   * <p>
   * This defines how encapsulated packets are serialized and deserialized. <br>
   * This method is called once by the global manager ({@link Claj}).
   */
  Serializer getPacketWrapperSerializer();
  
  // Client specific handling
  default void showTextMessage(String text) {}
  default void showMessage(MessageType message) {}
  default void showPopup(String text) {}
}
