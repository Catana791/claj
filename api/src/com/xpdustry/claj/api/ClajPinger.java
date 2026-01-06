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
import java.nio.ByteBuffer;

import arc.Core;
import arc.func.Cons;
import arc.net.Client;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.net.FrameworkMessage.Ping;
import arc.struct.LongMap;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;


/** 
 * Note that {@link #pingHost}, {@link #requestRoomList} and {@link #joinRoom} are async operations.<br>
 * If one of them are called while another is running, the last one is canceled.
 */
public class ClajPinger extends Client {
  public static final short NO_PASSWORD = -1;
  public static int defaultTimeout = 5000; //ms
  public static int defaultPingTimeout = 2000; //ms
  
  // Redefine some internal state
  protected int lastPingID;
  protected long lastPingSendTime;
  protected volatile boolean shutdown = true;
  
  protected Cons<Integer> pingSuccess;
  protected Cons<Exception> pingFailed;
  protected boolean pingReceived, pinging;
  
  protected Cons<ClajRoom> listInfo;
  protected Cons<Long> listUpdated;
  protected Runnable listDone;
  protected Cons<Exception> listFailed;
  protected long lastRoomId = -1;
  protected final LongMap<ClajRoom> rooms = new LongMap<>();
  protected boolean listing;
  
  protected Runnable joinSuccess;
  protected Cons<RejectReason> joinDenied;
  protected Cons<Exception> joinFailed;
  protected long requestedRoom = -1;
  protected boolean joining;

  public ClajPinger() {
    super(8192, 8192, new Serializer());
    ClientReceiver receiver = new ClientReceiver(this, false);
    
    receiver.handle(PingReply.class, p -> {
      if (p.id == lastPingID-1)
        runPingSuccess(getReturnTripTime());
    });
    
    receiver.handle(RoomJoinAcceptedPacket.class, p -> {
      if (p.roomId != -1 && p.roomId == requestedRoom)
        runJoinSuccess();
    });
    receiver.handle(RoomJoinDeniedPacket.class, p -> {
      if (p.roomId != -1 && p.roomId == requestedRoom)
        runJoinDenied(p.reason);
    });
    
    receiver.handle(RoomListPacket.class, p -> {
      lastRoomId = p.hasNext ? -1 : p.rooms.peekKey();
      for (int i=0; i<p.rooms.size; i++) {
        runListInfo(p.rooms.getKeyAt(i), p.rooms.getValueAt(i));
        requestRoomInfo(p.rooms.getKeyAt(i));
      }
    });
    receiver.handle(RoomInfoPacket.class, p -> {
      runListUpdated(p.roomId, p.state);
      if (p.roomId != -1 && lastRoomId == p.roomId) 
        runListDone();
    });
  }
  
  @Override
  public void updateReturnTripTime() {
    lastPingID++;
    lastPingSendTime = System.currentTimeMillis();
    super.updateReturnTripTime();
  }
  
  @Override
  public void update(int timeout) throws IOException {
    super.update(timeout);
    if (pinging && !pingReceived && 
        System.currentTimeMillis() - lastPingSendTime > defaultPingTimeout) 
      runPingFailed(new RuntimeException("Ping timed out"));
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
  public void close(DcReason reason) {
    cancel();
    super.close(reason);
  }
  
  /** Cancel running operation. */
  public void cancel() {
    if (pinging) runPingFailed(new RuntimeException("Ping canceled"));
    if (listing) runListFailed(new RuntimeException("Room listing canceled"));
    if (joining) runJoinFailed(new RuntimeException("Room join canceled"));
  }
  
  public boolean isRunning() {
    return !shutdown;
  }
  
  public boolean isWorking() {
    return pinging || listing || joining;
  }
  
  // Helpers
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { Core.app.post(run); }
  
  protected void resetPingState(Cons<Integer> success, Cons<Exception> failed) {
    pingSuccess = success;
    pingFailed = failed;
    pingReceived = false;
    pinging = false;
  }
  
  protected void runPingSuccess(int time) {
    if (pingSuccess != null) postTask(pingSuccess, time);
    resetPingState(null, null);
    pingReceived = true;
    close();
  }
  
  protected void runPingFailed(Exception e) {
    if (pingFailed != null) postTask(pingFailed, e);
    resetPingState(null, null);
    close();
  }
  
  protected void resetListState(Cons<ClajRoom> room, Cons<Long> updated, Runnable done, 
                                Cons<Exception> failed) {
    listInfo = room;
    listUpdated = updated;
    listDone = done;
    listFailed = failed;
    lastRoomId = -1;
    rooms.clear();
    listing = false;
  }
  
  protected void runListInfo(long roomId, boolean isProtected) {
    // Avoid creating useless objects if the callback is not defined.
    if (listInfo == null) return;
    ClajRoom room = new ClajRoom(roomId);
    room.isPublic = true; // If the server includes it, it must be public
    room.isProtected = isProtected;
    rooms.put(roomId, room);
    postTask(listInfo, room);
  }

  protected void runListUpdated(long roomId, GameState state) {
    if (listUpdated == null) return;
    ClajRoom room = rooms.get(roomId);
    if (room == null) return; // should not be possible
    room.state = state;
    postTask(listUpdated, roomId);
  }
  
  protected void runListDone() {
    if (listDone != null) postTask(listDone);
    resetListState(null, null, null, null);
    close();
  }
  
  protected void runListFailed(Exception e) {
    if (listFailed != null) postTask(listFailed, e);
    resetListState(null, null, null, null);
    close();
  }
  
  protected void resetJoinState(Runnable success, Cons<RejectReason> reject, Cons<Exception> failed) {
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
   * This also ensures that the client is running before connection.
   */
  public void connect(String host, int port) throws IOException {
    if (!isRunning()) start();
    connect(defaultTimeout, host, port, port);
  }
  
  public void pingHost(String ip, int port, Cons<Integer> success, Cons<Exception> failed) {
    if (!isRunning()) start();
    try {
      connect(defaultPingTimeout, ip, port, port);
      resetPingState(success, failed);
      pinging = true;
      updateReturnTripTime();
    } catch (Exception e) { 
      runPingFailed(e);
    }
  }
  
  public void requestRoomList(String ip, int port, Cons<ClajRoom> room, Cons<Long> updated,
                              Runnable done, Cons<Exception> failed) {
    try {
      connect(ip, port);
      resetListState(room, updated, done, failed);
      listing = true;
      requestRoomList();
    } catch (Exception e) { 
      runListFailed(e); 
    }
  }


  public void joinRoom(String ip, int port, long roomId, Runnable success, Cons<RejectReason> reject, 
                       Cons<Exception> failed) {
    joinRoom(ip, port, roomId, NO_PASSWORD, success, reject, failed);
  }
  
  public void joinRoom(String ip, int port, long roomId, short password, Runnable success, 
                       Cons<RejectReason> reject, Cons<Exception> failed) {
    try {
      connect(ip, port);
      resetJoinState(success, reject, failed);
      requestedRoom = roomId;
      joining = true;
      requestRoomJoin(roomId, password);
    } catch (Exception e) { 
      runJoinFailed(e); 
    }
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
  
  
  /** A custom serializer is needed to know if the received ping is the reply of the one sent. =/ */
  static class Serializer extends ClajProxy.Serializer {
    @Override
    public Object read(ByteBuffer buffer) {
      if (buffer.get() == ClajNet.frameworkId) {
        FrameworkMessage f = readFramework(buffer);
        return f instanceof Ping p && p.isReply ? new PingReply(p) : f;
      }
      buffer.position(buffer.position()-1);
      return super.read(buffer);
    }
  }
  
  static class PingReply extends Ping implements Packet {
    public PingReply(Ping ping) {
      id = ping.id;
      isReply = ping.isReply;
    }
  }
}
