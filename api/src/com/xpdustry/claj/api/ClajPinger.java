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

import java.io.IOException;
import java.nio.channels.Selector;

import arc.Core;
import arc.func.Cons;
import arc.func.Cons2;
import arc.net.Client;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.struct.Seq;
import arc.util.Reflect;

import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;


// FIXME: see ClajPingerManager comment
/** 
 * Note that {@link #pingHost}, {@link #requestRoomList} and {@link #joinRoom} are async operations.<br>
 * If one of them are called while another is running, the last one is canceled.
 */
public class ClajPinger extends Client {
  public static final short NO_PASSWORD = -1;
  public static int defaultTimeout = 5000; //ms
  public static int defaultPingTimeout = 5000; //ms

  protected String connectHost;
  protected int connectPort;
  protected volatile boolean shutdown = true, connecting;
  /** If {@code true}, current and future operations will be canceled. */
  public volatile boolean canceling;
  
  protected Cons<ServerState> pingSuccess;
  protected Cons<Exception> pingFailed;
  protected volatile long lastPing;
  protected volatile boolean pingReceived, pinging;
  
  protected Cons<Seq<ClajRoom>> listInfo;
  protected Cons<Exception> listFailed;
  protected volatile boolean listing;
  
  protected Runnable joinSuccess;
  protected Cons<RejectReason> joinDenied;
  protected Cons<Exception> joinFailed;
  protected volatile long requestedRoom = -1;
  protected volatile boolean joining;

  public ClajPinger() {
    super(8192, 8192, new ClajSerializer());
    ClientReceiver receiver = new ClientReceiver(this, false); // no need to delegate to the main thread

    receiver.handle(RoomJoinAcceptedPacket.class, p -> {
      if (p.roomId != -1 && p.roomId == requestedRoom)
        runJoinSuccess();
    });
    receiver.handle(RoomJoinDeniedPacket.class, p -> {
      if (p.roomId != -1 && p.roomId == requestedRoom)
        runJoinDenied(p.reason);
    });
    
    receiver.handle(RoomListPacket.class, p -> {
      runListInfo(p.size, p.rooms, p.isProtected, p.states);
    });
    
    receiver.handle(ServerInfoPacket.class, p -> {
      runPingSuccess(p.majorVersion);
    });
  }

  @Override
  public void update(int timeout) throws IOException {
    super.update(canceling ? 0 : timeout);
    if (pinging && !pingReceived && System.currentTimeMillis() - lastPing >= defaultPingTimeout) 
      runPingFailed(new RuntimeException("Ping timed out"));
    if (canceling) close();
  }
  
  @Override
  public void run() {
    shutdown = false;
    super.run();
  }
  
  @Override
  public void start() {
    if (getUpdateThread() != null) shutdown = true;
    super.start();
  }
  
  @Override
  public void stop() {
    if(shutdown) return;
    super.stop();
    shutdown = true;
  }
  
  @Override
  public void close() {
    if (canceling) {
      close(DcReason.closed);
      // Makes #close() doesn't wait for 'updateLock', which will makes #connect() cancelable
      Reflect.<Selector>get(Client.class, this, "selector").wakeup();  
    } else super.close();
  }
  
  @Override
  public void close(DcReason reason) {
    cancel();
    super.close(reason);
  }
  
  /** Cancel running operation. */
  public synchronized void cancel() {
    if (pinging) runPingFailed(new RuntimeException("Ping canceled"));
    if (listing) runListFailed(new RuntimeException("Room listing canceled"));
    if (joining) runJoinFailed(new RuntimeException("Room join canceled"));
    if (connecting) {
      //Reflect.set(Client.class, this, "tcpRegistered", true);
      //Reflect.set(Client.class, this, "udpRegistered", true);
      super.close(DcReason.closed);
    }
  }
  
  public boolean isRunning() {
    return !shutdown;
  }
  
  public boolean isConnecting() {
    return connecting;
  }
  
  public synchronized boolean isWorking() {
    return pinging || listing || joining;
  }
  
  // Helpers
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { Core.app.post(run); }
  
  protected synchronized void resetPingState(Cons<ServerState> success, Cons<Exception> failed) {
    lastPing = System.currentTimeMillis();
    pingSuccess = success;
    pingFailed = failed;
    pingReceived = false;
    pinging = false;
  }
  
  protected void runPingSuccess(int majorVersion) {
    pingReceived = true;
    if (pingSuccess != null) {
      int ping = (int)(System.currentTimeMillis() - lastPing);
      postTask(pingSuccess, new ServerState(connectHost, connectPort, majorVersion, ping));
    }
    resetPingState(null, null);
    close();
  }
  
  protected void runPingFailed(Exception e) {
    if (pingFailed != null) postTask(pingFailed, e);
    resetPingState(null, null);
    close();
  }
  
  protected synchronized void resetListState(Cons<Seq<ClajRoom>> rooms, Cons<Exception> failed) {
    listInfo = rooms;
    listFailed = failed;
    listing = false;
  }
  
  protected void runListInfo(int size, long[] rooms, boolean[] isProtected, GameState[] states) {
    // Avoid creating useless objects if the callback is not defined.
    if (listInfo == null) return;
    Seq<ClajRoom> roomList = new Seq<>(size);
    for (int i=0; i<size; i++) {
      if (rooms[i] == ClajProxy.UNCREATED_ROOM) continue; // ignore invalid rooms
      roomList.add(new ClajRoom(rooms[i], true, isProtected[i], states[i], 
                                new ClajLink(connectHost, connectPort, rooms[i])));
    }
      
    postTask(listInfo, roomList);
    resetListState(null, null);
    close();
  }
  
  protected void runListFailed(Exception e) {
    if (listFailed != null) postTask(listFailed, e);
    resetListState(null, null);
    close();
  }
  
  protected synchronized void resetJoinState(Runnable success, Cons<RejectReason> reject, Cons<Exception> failed) {
    joinSuccess = success;
    joinDenied = reject;
    joinFailed = failed;
    requestedRoom = -1;
    joining = false;
  }
  
  protected void runJoinSuccess() {
    if (joinSuccess != null) postTask(joinSuccess);
    resetJoinState(null, null, null);
    close();
  }
  
  protected void runJoinDenied(RejectReason reason) {
    if (joinDenied != null) postTask(joinDenied, reason);
    resetJoinState(null, null, null);
    close();
  }
  
  protected void runJoinFailed(Exception e) {
    if (joinFailed != null) postTask(joinFailed, e);
    resetJoinState(null, null, null);
    close();
  }
  
  /** 
   * Connect used {@link #defaultTimeout} and same {@code port} for TCP and UDP. <br>
   * This also ensures that the client is running before connection, and can be canceled.
   */
  public void connect(String host, int port) throws IOException {
    if (!isRunning()) start();
    connecting = true;
    connectHost = host;
    connectPort = port;
    try { connect(defaultTimeout, host, port, port); }
    finally { connecting = false; }
  }
  
  public void pingHost(String host, int port, Cons<ServerState> success, Cons<Exception> failed) {
    close();
    //arc.util.Log.info("pinger: @, @, @", canceling, Thread.currentThread().getName(), System.currentTimeMillis());
    resetPingState(success, failed);
    pinging = true;
    if (canceling) {
      cancel();
      return;
    }
    try { connect(host, port); }
    catch (Exception e) {
      resetPingState(success, failed);
      runPingFailed(e);
      return;
    }
    requestServerStatus();
  }
  
  public void requestRoomList(String host, int port, Cons<Seq<ClajRoom>> rooms, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) { 
        resetListState(rooms, failed);
        runListFailed(e); 
        return;
      }
    }
    resetListState(rooms, failed);
    listing = true;
    if (canceling) cancel();
    else requestRoomList();
  }


  public void joinRoom(String host, int port, long roomId, Runnable success, Cons<RejectReason> reject, 
                       Cons<Exception> failed) {
    joinRoom(host, port, roomId, NO_PASSWORD, success, reject, failed);
  }
  
  public void joinRoom(String host, int port, long roomId, short password, Runnable success, 
                       Cons<RejectReason> reject, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) { 
        resetJoinState(success, reject, failed);
        runJoinFailed(e); 
        return;
      }
    }
    resetJoinState(success, reject, failed);
    requestedRoom = roomId;
    joining = true;  
    if (canceling) cancel();
    else requestRoomJoin(roomId, password);
  }

  public void requestRoomInfo(String host, int port, long roomId, Cons2<Long, GameState> info) {
    //TODO
  }
  
  protected void requestServerStatus() {
    sendUDP(FrameworkMessage.discoverHost);
  }
  
  protected void requestRoomInfo(long roomId) {
    RoomInfoRequestPacket p = new RoomInfoRequestPacket();
    p.roomId = roomId;
    sendTCP(p);
  }
  
  protected void requestRoomList() {
    sendTCP(new RoomListRequestPacket());
  }
  
  protected void requestRoomJoin(long roomId, short password) {
    RoomJoinPacket p = new RoomJoinPacket();
    p.roomId = roomId;
    p.password = password;
    sendTCP(p);
  }
}
