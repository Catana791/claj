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

package com.xpdustry.claj.client.dialogs;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.ui.Button;
import arc.scene.ui.layout.*;
import arc.struct.ArrayMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;

import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.client.*;
import com.xpdustry.claj.common.status.CloseReason;


public class CreateRoomDialog extends BaseDialog {
  ClajLink link;
  Server selected;
  Button bselected;
  final Table custom = new Table(), online = new Table();
  boolean refreshingOnline;

  public CreateRoomDialog() {
    super("@claj.manage.name");
    Events.run(EventType.HostEvent.class, this::closeRoom);

    cont.defaults().width(Vars.mobile ? 480f : 800f);
    
    makeButtonOverlay();
    addCloseButton();
    buttons.button("@claj.manage.create", Icon.add, this::createRoom)
           .disabled(b -> Claj.get().proxies.isRoomCreated() || selected == null);
    if (Vars.mobile) buttons.row();
    buttons.button("@claj.manage.delete", Icon.cancel, this::closeRoom)
           .disabled(b -> Claj.get().proxies.isRoomClosed());
    buttons.button("@copylink", Icon.copy, this::copyLink).disabled(b -> link == null);
    
    keyDown(KeyCode.f5, this::refreshAll);
    shown(() -> {
      // Just to give time to this dialog to open
      Time.run(7f, this::refreshAll);
    });

    cont.top();
    cont.pane(inner -> {
      inner.table(hosts -> {
        // Description
        hosts.table(table -> {
          table.labelWrap("@claj.manage.tip").left().growX();
          table.button(Icon.settings, () -> ClajUi.settings.show()).right().padLeft(10).growY()
               .tooltip("@claj.settings.title");  
        }).padBottom(24).growX().row();
        
        // Custom servers
        section("@claj.manage.custom-servers", custom, hosts, () -> ClajUi.add.show((n, h) -> {
          ClajServers.custom.put(n, h);
          ClajServers.saveCustom();
          refreshCustom();
        }), this::refreshCustom);
        
        // Public servers
        section("@claj.manage.public-servers", online, hosts, null, this::refreshOnline);
      }).padRight(5).grow();
      
      // Give extra space for buttons
      inner.marginBottom(Vars.mobile ? 140f : 70f); 
    }).with(s -> {
      s.setForceScroll(false, true);
      s.setScrollingDisabled(true, false);
    });

    // Add the 'Manage CLaJ room' button in pause menu
    addButton();
  }
  
  void addButton() {  
    Vars.ui.paused.shown(() -> {
      Table root = Vars.ui.paused.cont;
      root.row();
      @SuppressWarnings("rawtypes")
      Seq<Cell> buttons = root.getCells();

      if (Vars.mobile) {
        root.buttonRow("@claj.manage.name", Icon.planet, this::show)
            .disabled(button -> !Vars.net.server()).row();
        return;
        
      // Makes it compatible for foo's client users by checking the hosting button.
      // 'colspan' is normally at 2 on vanilla.
      // Also there is no way to get this property, so we need reflection.
      } else if (Reflect.<Integer>get(buttons.get(buttons.size-2), "colspan") == 2) 
        root.button("@claj.manage.name", Icon.planet, this::show).colspan(2).width(450f)
            .disabled(button -> !Vars.net.server()).row();   
      
      // Probably the foo's client, use a normal button
      else 
        root.button("@claj.manage.name", Icon.planet, this::show)
            .disabled(button -> !Vars.net.server()).row(); 
      
      // move the claj button above the quit button
      buttons.swap(buttons.size-1, buttons.size-2);
    });
  }
  
  public void refreshAll() {
    refreshCustom();
    refreshOnline();
  }
  
  public void refreshCustom() {
    ClajServers.loadCustom();
    setupServers(ClajServers.custom, custom, 
      s -> ClajUi.add.show(s.name, s.get(), (n, a) -> {
        int index = ClajServers.custom.indexOfKey(s.name);
        ClajServers.custom.setKey(index, n);
        ClajServers.custom.setValue(index, a);
        ClajServers.saveCustom();
        refreshCustom();          
      }), 
      s -> Vars.ui.showConfirm("@confirm", "@server.delete", () -> {
        ClajServers.custom.removeKey(s.name);
        ClajServers.saveCustom();
        refreshCustom();   
      })
    );
  }
  
  public void refreshOnline() {
    if (refreshingOnline) return; // Avoid to re-trigger a refresh while refreshing
    refreshingOnline = true;
    Claj.get().stopPingers(); // cancel previous pings
    
    online.clear();
    online.button(b -> 
      b.table(t -> {
        t.add("@claj.servers.fetching").padRight(3);
        t.label(() -> Strings.animated(Time.time, 4, 11, ".")).color(Pal.accent);
      }).center(), () -> {}
    ).growX().padTop(5).padBottom(5);
    
    ClajServers.refreshOnline(() -> {
      refreshingOnline = false;
      if (ClajServers.online.isEmpty()) {
        online.clear();
        online.button("@claj.servers.empty", () -> {}).growX().padTop(5).padBottom(5).row();
      } else setupServers(ClajServers.online, online, null, null);
    }, e -> {
      refreshingOnline = false;
      online.clear();
      online.button("@claj.servers.check-internet", () -> {}).growX().padTop(5).padBottom(5).row();
      Vars.ui.showException("@claj.servers.fetch-failed", e);
    }); 
  }
  
  public void section(String label, Table src, Table dest, Runnable add, Runnable refresh) {
    Collapser coll = new Collapser(src, false);
    dest.table(head -> {
      head.add(label, Pal.accent).pad(5).growX().left().bottom();
      if (add != null)
        head.button(Icon.add, Styles.emptyi, add).size(40f).padRight(3).right().tooltip("@server.add");
      if (refresh != null)
        head.button(Icon.refresh, Styles.emptyi, refresh).size(40f).padRight(3).right().tooltip("@servers.refresh");
      head.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle()).size(40f).padRight(5).right()
          .update(i -> i.getStyle().imageUp = coll.isCollapsed() ? Icon.downOpen : Icon.upOpen)
          .tooltip(t -> t.label(() -> "@servers." + (coll.isCollapsed() ? "show" : "hide")));
    }).growX().row();
    dest.image().pad(5).height(3).color(Pal.accent).growX().row();
    dest.add(coll).padBottom(10).growX().row();  
  }
  
  public void setupServers(ArrayMap<String, String> servers, Table table, Cons<Server> edit, Cons<Server> delete) {
    selected = null;// in case of
    table.clear();

    for (ObjectMap.Entry<String, String> e : servers) {
      Server server = new Server();
      server.name = e.key;
      server.set(e.value);
      
      Button button = new Button(); 
      button.getStyle().checkedOver = button.getStyle().checked = button.getStyle().over;
      button.setProgrammaticChangeEvents(true);
      button.clicked(() -> {
        selected = server;
        bselected = button;
      });
      table.add(button).checked(b -> bselected == b).growX().padTop(5).padBottom(5).row();

      Table label = new Table().center();
      Table inner = new Table();
      button.clearChildren();
      button.stack(label, inner).growX().row();
      
      // Cut in two line for mobiles or if the name is too long
      if (Vars.mobile || (e.key + " (" + e.value + ')').length() > 54) {
        label.add(e.key).pad(5, 5, 0, 5).expandX().row();
        label.add("[lightgray](" + e.value + ')').pad(5, 0, 5, 5).expandX();
      } else label.add(e.key + " [lightgray](" + e.value + ')').pad(5).expandX();

      inner.setColor(Pal.gray);
      inner.table(ping -> pingServer(server, ping)).margin(0).padLeft(5).padRight(5).left().fillX();
      inner.add().expandX();
      
      if (edit != null) {
        Cell<?> editb = 
          inner.button(Vars.mobile ? Icon.pencil : Icon.pencilSmall, Styles.emptyi, () -> edit.get(server))
               .right().tooltip("@server.edit");
        if (!Vars.mobile) editb.pad(4f);
        else editb.size(30f).pad(2, 5, 2, 5);
      }
      
      if (delete != null) {
        Cell<?> deleteb = 
          inner.button(Vars.mobile ? Icon.trash : Icon.trashSmall, Styles.emptyi, () -> delete.get(server))
               .right().tooltip("@server.del");
        if (!Vars.mobile) deleteb.pad(2f);
        else deleteb.size(30f).pad(2, 5, 2, 5);
      }
    }
  }
  
  void pingServer(Server server, Table dest) {
    dest.clear();
    dest.label(() -> Strings.animated(Time.time, 4, 11, ".")).pad(2).color(Pal.accent).left();
    
    Claj.get().pingHost(server.address, server.port, s -> {
      server.compatible = s.majorVersion() == Claj.get().provider.getVersion();
      server.outdated = s.majorVersion() < Claj.get().provider.getVersion();
      
      dest.clear();
      if (server.compatible) dest.image(Icon.ok, Color.green).padRight(7).left();
      else dest.image(Icon.warning, Color.yellow).padBottom(3).left().get().scaleBy(-0.22f);
      if (Vars.mobile) dest.row();
      dest.add(s.ping() + "ms", Color.lightGray, 0.91f).left();
    }, e -> {
      dest.clear();
      dest.image(Icon.cancel, Color.red).left();
    });
  }

  public void createRoom() {
    if (selected == null) return;
    link = null;
    
    // Pre-check
    if (!selected.compatible) {
      showError(selected.outdated ? CloseReason.outdatedServer : CloseReason.outdatedClient);
      return;
    }
    
    Vars.ui.loadfrag.show("@claj.manage.creating-room");
    // Disconnect the client if the room is not created after 10 seconds
    Timer.Task t = Timer.schedule(this::closeRoom, 10);
    Claj.get().createRoom(selected.address, selected.port, l -> {
      Vars.ui.loadfrag.hide();
      t.cancel();
      link = l;
    }, c -> {
      Vars.ui.loadfrag.hide();
      t.cancel();
      if (c != null) showError(c);
      else if (link == null) Vars.ui.showErrorMessage("@claj.manage.room-creation-failed");
      link = null;
    }, e -> {
      Vars.net.handleException(e);
      t.cancel();
    });
  }
  
  public void showError(CloseReason reason) {
    switch (reason) {
      case closed: 
      case serverClosed:
        Vars.ui.showText("", "@claj.room." + Strings.camelToKebab(reason.name()));
        break;
      default:
        Vars.ui.showErrorMessage("@claj.room." + Strings.camelToKebab(reason.name()));
    }
  }
  
  public void closeRoom() {
    Claj.get().proxies.closeRoom();
    link = null;
  }
  
  public void copyLink() {
    if (link == null) return;
    Core.app.setClipboardText(link.toString());
    Vars.ui.showInfoFade("@copied");
  }
  
  
  public static class Server {
    public String address, name, error, last;
    public int port;
    public boolean wasValid, compatible = true, outdated;
    
    public boolean set(String host) {
      if (host.equals(last)) return wasValid;
      address = error = null;
      port = 0;
      last = host;
      
      if (host.isEmpty()){
        error = "@claj.manage.missing-host";
        return wasValid = false;
      }
      try{
        boolean isIpv6 = Strings.count(host, ':') > 1;
        if(isIpv6 && host.lastIndexOf("]:") != -1 && host.lastIndexOf("]:") != host.length() - 1){
          int idx = host.indexOf("]:");
          address = host.substring(1, idx);
          port = Integer.parseInt(host.substring(idx + 2));
          if (port < 0 || port > 0xFFFF) throw new Exception();
        }else if(!isIpv6 && host.lastIndexOf(':') != -1 && host.lastIndexOf(':') != host.length() - 1){
          int idx = host.lastIndexOf(':');
          address = host.substring(0, idx);
          port = Integer.parseInt(host.substring(idx + 1));
          if (port < 0 || port > 0xFFFF) throw new Exception();
        }else{
          error = "@claj.manage.missing-port";
          return wasValid = false;
        }
        return wasValid = true;
      }catch(Exception e){
        error = "@claj.manage.invalid-port";
        return wasValid = false;
      }
    }

    public String get(){
      if(!wasValid){
        return "";
      }else if(Strings.count(address, ':') > 1){
        return "[" + address + "]:" + port;
      }else{
        return address +  ":" + port;
      }
    }
  }
}
