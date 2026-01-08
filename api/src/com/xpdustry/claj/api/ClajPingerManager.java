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

import arc.func.Cons;
import arc.func.Cons2;
import arc.struct.Queue;
import arc.util.Threads;

import com.xpdustry.claj.common.status.RejectReason;
import com.xpdustry.claj.common.status.ServerState;


// FIXME: this works pretty well, but keep refreshing the server list will fill up pingers, if a server is unreachable.
public class ClajPingerManager {
  protected final int workers;
  protected final ClajProvider provider;
  protected int created;
  protected final ClajPinger[] pingers;
  protected final boolean[] reserved;
  protected final Queue<Cons2<ClajPinger, Runnable>> queue = new Queue<>();
  
  public ClajPingerManager(ClajProvider provider, int workers) {
    this.workers = workers;
    this.provider = provider;
    pingers = new ClajPinger[workers];
    reserved = new boolean[workers];
  }
  
  public String getPingerName(int index) {
    return workers == 1 ? "Claj Pinger" : "Claj Pinger " + (index+1);
  }
  public boolean hasCreatedPingers() {
    return created > 0;
  }
  
  public int createdPingers() {
    return created;
  }
  
  public int pingers() {
    return workers;
  }
  
  public ClajProvider provider() {
    return provider;
  }
  
  public ClajPinger ensurePingerCreated(int index) {
    if (pingers[index] == null) {
      pingers[index] = new ClajPinger();
      created++;
    }
    return pingers[index];
  }

  public ClajPinger ensurePingerProxyStarted(int index) {
    ClajPinger pinger = get(index);
    if (!pinger.isRunning()) Threads.daemon(getPingerName(index), pinger); 
    return pinger;
  }

  /** @return the first pinger. */
  public ClajPinger get() { return get(0); }
  public ClajPinger get(int index) {
    return ensurePingerCreated(index);
  }
  
  public ClajPinger getOrNull(int index) {
    return pingers[index];
  }
  
  /** Search for a pinger that's not working. */
  public ClajPinger findFree() {
    int index = findFreeI();
    return index == -1 ? null : get(index);
  }
    
  public int findFreeI() {
    // First check if empty
    if (created == 0) {
      get();
      return 0;
    }
    // Prioritize existing free pingers
    for(int i=0; i<workers; i++) {
      if (pingers[i] != null && !isBusy(i)) return i;
    }
    // Then empty slots
    for(int i=0; i<workers; i++) {
      if (pingers[i] == null && !reserved[i]) {
        get(i);
        return i;
      }
    }
    return -1;
  }
  
  public boolean isBusy(int index) {
    if (reserved[index]) return true;
    ClajPinger pinger = pingers[index];
    return pinger != null && pinger.isWorking(); 
  }
  
  /** Dispose all pingers. */
  public void dispose() {
    for (ClajPinger pinger : pingers) {
      if (pinger == null) continue;
      pinger.stop();
      try { pinger.dispose(); }
      catch (Exception ignored) {}  
    }
  }
  
  /** Stops all pingers and cancel the queue. */
  public void stop() {
    //arc.util.Log.info("manager start: @, @", Thread.currentThread().getName(), System.currentTimeMillis());
    for (ClajPinger pinger : pingers) {
      if (pinger == null) continue;
      pinger.canceling = true;
      pinger.close(); // be sure
    }
    //arc.util.Log.info("manager in: @, @", Thread.currentThread().getName(), System.currentTimeMillis());
    // Run rest of the queue
    if (!queue.isEmpty()) {
      // We don't care about who will receive the task, this will not block anyway
      while (!queue.isEmpty()) {
        queue.removeFirst().get(get(), () -> {}); 
        //arc.util.Log.info("dequeueing: @", queue.size);
      }
    }
    for (ClajPinger pinger : pingers) {
      if (pinger == null) continue;
      pinger.close(); // be sure
      pinger.canceling = false;
    }
    //arc.util.Log.info("manager out: @, @", Thread.currentThread().getName(), System.currentTimeMillis());
  }
  
  /** Queue the task if all pingers are busy. */
  protected void submit(Cons2<ClajPinger, Runnable> task) {
    int index = findFreeI();
    if (index != -1) submit(index, task);
    else queue.add(task);
  }

  protected void submit(int index, Cons2<ClajPinger, Runnable> task) {
    reserved[index] = true;
    ClajPinger pinger = get(index);
    provider.getExecutor().submit(() -> task.get(pinger, () -> {
      //arc.util.Log.info("pinger @ finished. queue empty? @", index, queue.isEmpty());
      if (pinger.canceling || queue.isEmpty()) reserved[index] = false;
      // Keep reserved, execute next
      else submit(index, queue.removeFirst());
    }));
  }

  public void joinRoom(ClajLink link, Runnable success, Cons<RejectReason> reject, Cons<Exception> failed) {
    joinRoom(link, ClajPinger.NO_PASSWORD, success, reject, failed);
  }
  
  public void joinRoom(ClajLink link, short password, Runnable success, Cons<RejectReason> reject,
                       Cons<Exception> failed) {
    if (link == null) return;
    submit((pinger, finished) -> {
      pinger.joinRoom(link.host, link.port, link.roomId, password, () -> {
        // Need to run on main thread for vars access
        provider.connectClient(link.host, link.port, success);
        finished.run();
      }, reason -> {
        if (reject != null) reject.get(reason);
        finished.run();
      }, e -> {
        if (failed != null) failed.get(e);
        finished.run();
      });
    });
  }

  /** @apiNote async operation but blocking new tasks if a ping is already in progress */
  public void pingHost(String ip, int port, Cons<ServerState> success, Cons<Exception> failed) {
    submit((pinger, finished) -> {
      pinger.pingHost(ip, port, i -> {
        if (success != null) success.get(i);
        finished.run();
      }, e -> {
        if (failed != null) failed.get(e);
        finished.run();
      });
    });
  }
  
  public void serverRooms(String ip, int port, Cons<ClajRoom> room, Cons<Long> updated, 
                          Runnable done, Cons<Exception> failed) {
    submit((pinger, finished) -> {
      pinger.requestRoomList(ip, port, room, updated, () -> {
        if (done != null) done.run();
        finished.run();
      }, error -> {
        if (failed != null) failed.get(error);
        finished.run();
      });
    });
  }
}