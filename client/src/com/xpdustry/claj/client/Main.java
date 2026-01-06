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

import arc.Events;
import arc.util.Timer;

import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.mod.Mods;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.client.dialogs.*;

public class Main extends Mod {
  public static MindustryClajProvider provider;
  public static JoinViaClajDialog joinDialog;
  public static CreateClajRoomDialog createDialog;
  public static RoomPasswordDialog passwordDialog;
  public static RoomBrowserDialog browserDialog;
  
  @Override
  public void init() {
    provider = new MindustryClajProvider();
    Claj.init(provider);
    ClajUpdater.schedule();
    initEvents();

    joinDialog = new JoinViaClajDialog();
    createDialog = new CreateClajRoomDialog();
    passwordDialog = new RoomPasswordDialog();
    browserDialog = new RoomBrowserDialog();
  }
  
  /** Automatically closes the rooms when quitting the game. */
  public void initEvents() {
    // Pretty difficult to know when the player quits the game, 
    // there is no event and StateChangeEvent is not reliable for that...
    Vars.ui.paused.hidden(() -> {
      Timer.schedule(() -> {
        if (!Vars.net.active() || Vars.state.isMenu()) Claj.get().closeRooms();
      }, 1f);
    });
    Events.run(EventType.HostEvent.class, Claj.get()::closeRooms);
    Events.run(EventType.ClientPreConnectEvent.class, Claj.get()::closeRooms);
  }
  
  /** Cached meta to avoid searching every times. */
  private static Mods.ModMeta meta;
  /** @return the mod meta, using this class. */
  public static Mods.ModMeta getMeta() {
    if (meta != null) return meta;
    Mods.LoadedMod load = Vars.mods.getMod(Main.class);
    if(load == null) throw new IllegalArgumentException("Mod is not loaded yet (or missing)!");
    return meta = load.meta;
  }
}
