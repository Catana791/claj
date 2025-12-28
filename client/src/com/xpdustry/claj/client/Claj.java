/**
 * This file is part of CLaJ. The system that allows you to play with your friends, 
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025  Xpdustry
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

package com.xpdustry.claj.client;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

import arc.Events;
import arc.func.Cons;
import arc.net.Client;
import arc.net.NetSerializer;
import arc.util.Threads;
import arc.util.Time;
import arc.util.Timer;

import mindustry.Vars;
import mindustry.game.EventType;

import com.xpdustry.claj.common.ClajPackets.CloseReason;
import com.xpdustry.claj.common.packets.RoomJoinPacket;


public class Claj {
  static {
    // Pretty difficult to know when the player quits the game, 
    // there is no event and StateChangeEvent is not reliable for that...
    Vars.ui.paused.hidden(() -> {
      Timer.schedule(() -> {
        if (!Vars.net.active() || Vars.state.isMenu()) closeRoom();
      }, 1f);
    });
    Events.run(EventType.HostEvent.class, Claj::closeRoom);
    Events.run(EventType.ClientPreConnectEvent.class, Claj::closeRoom);
    Events.run(EventType.DisposeEvent.class, () -> {
      disposeRoom();
      disposePinger();
    });
  } 
  
  private static ClajProxy room;
  private static Client pinger;
  private static ExecutorService worker = Threads.unboundedExecutor("CLaJ Worker", 1);
  private static NetSerializer tmpSerializer = new ClajProxy.Serializer();
  private static ByteBuffer tmpBuffer = ByteBuffer.allocate(16);// we only need 10 bytes for the room join packet
  private static Thread roomThread, pingerThread;

  public static boolean isRoomClosed() {
    return room == null || !room.isConnected();
  }

  /** @apiNote async operation */
  public static void createRoom(String ip, int port, Cons<ClajLink> done, Cons<Throwable> failed, 
                                Cons<CloseReason> disconnected) {
    if (room == null || roomThread == null || !roomThread.isAlive()) 
    // TODO: this probably fix the issue where the room keeps closing when switching of app on android
      roomThread = Threads./*deamon*/thread("CLaJ Proxy", room = new ClajProxy());  
    
    worker.submit(() -> {
      synchronized (roomThread) {
        try {
          if (room.isConnected()) 
            throw new IllegalStateException("Room is already created, please close it before.");
          room.connect(ip, port, id -> done.get(new ClajLink(ip, port, id)), disconnected);
        } catch (Throwable e) { failed.get(e); }  
      }
    });
  }
  
  public static ClajProxy getRoom() {
    return room;
  }
  
  /** Just close the room connection, doesn't delete it */
  public static void closeRoom() {
    if (room != null) room.close();
  }
  
  /** Delete properly the room */
  public static void disposeRoom() {
    if (room == null) return;
    room.stop();
    try { roomThread.join(1000); }
    catch (Exception ignored) {}
    roomThread.interrupt();
    try { room.dispose(); }
    catch (Exception ignored) {}
    roomThread = null;
    room = null;
  }

  public static void joinRoom(ClajLink link, Runnable success) {
    if (link == null) return;
    
    Vars.logic.reset();
    Vars.net.reset();

    Vars.netClient.beginConnecting();
    Vars.net.connect(link.host, link.port, () -> {
      if (!Vars.net.client()) return;
      
      // We need to serialize the packet manually
      tmpBuffer.clear();
      RoomJoinPacket p = new RoomJoinPacket();
      p.roomId = link.roomId;
      tmpSerializer.write(tmpBuffer, p);
      tmpBuffer.limit(tmpBuffer.position()).position(0);
      Vars.net.send(tmpBuffer, true);
      
      if (success != null) success.run();
    });
  }

  /** @apiNote async operation but blocking new tasks if a ping is already in progress */
  public static void pingHost(String ip, int port, Cons<Long> success, Cons<Exception> failed) {
    if (pinger == null || pingerThread == null || !pingerThread.isAlive()) 
      pingerThread = Threads.daemon("CLaJ Pinger", pinger = new Client(8192, 8192, tmpSerializer));

    worker.submit(() -> {
      synchronized (pingerThread) {
        long time = Time.millis();
        try { 
          // Connect successfully is enough.
          pinger.connect(2000, ip, port); 
          time = Time.timeSinceMillis(time);
          pinger.close();
          success.get(time);
        } catch (Exception e) { failed.get(e); }
      }
    });
  }
  
  public static void disposePinger() {
    if (pinger == null) return;
    pinger.stop();
    try { pingerThread.join(1000); }
    catch (Exception ignored) {}
    pingerThread.interrupt();
    try { pinger.dispose(); }
    catch (Exception ignored) {}
    pingerThread = null;
    pinger = null;
  }
}
