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
import arc.scene.ui.Button;
import arc.scene.ui.layout.*;
import arc.struct.ArrayMap;
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
  boolean customShown = true, onlineShown = true, refreshingOnline;

  public CreateRoomDialog() {
    super("@claj.manage.name");
    Events.run(EventType.HostEvent.class, this::closeRoom);
    
    cont.defaults().width(Vars.mobile ? 480f : 850f);
    
    makeButtonOverlay();
    addCloseButton();
    buttons.button("@claj.manage.create", Icon.add, this::createRoom)
           .disabled(b -> Claj.get().proxies.isRoomCreated() || selected == null);
    if (Vars.mobile) buttons.row();
    buttons.button("@claj.manage.delete", Icon.cancel, this::closeRoom)
           .disabled(b -> Claj.get().proxies.isRoomClosed());
    buttons.button("@claj.manage.copy", Icon.copy, this::copyLink).disabled(b -> link == null);
    
    shown(() -> {
      // Just to give time to this dialog to open
      Time.run(7f, () -> {
        refreshCustom();
        refreshOnline();  
      });
    });

    cont.pane(inner -> {
      inner.table(hosts -> {
        // Description
        hosts.table(table -> {
          table.labelWrap("@claj.manage.tip").left().growX();
          table.button(Icon.settings, ClajUi.settings::show).right().padLeft(10).growY()
               .tooltip("@claj.settings.title");  
        }).padBottom(24).growX().row();
        
        // Custom servers
        hosts.table(table -> {
          table.add("@claj.manage.custom-servers").pad(10).padLeft(0).color(Pal.accent).growX().left();
          table.button(Icon.add, Styles.emptyi, () -> ClajUi.add.show((n, h) -> {
            ClajServers.custom.put(n, h);
            ClajServers.saveCustom();
            refreshCustom();
          })).size(40f).right().padRight(3).tooltip("@server.add");
  
          table.button(Icon.refresh, Styles.emptyi, this::refreshCustom).size(40f).right().padRight(3)
               .tooltip("@servers.refresh");
          table.button(Icon.downOpen, Styles.emptyi, () -> customShown = !customShown)
               .update(i -> i.getStyle().imageUp = customShown ? Icon.downOpen : Icon.upOpen).size(40f).right()
               .tooltip(t -> t.label(() -> "@servers." + (customShown ? "hide" : "show")));
        }).growX().row();
        hosts.image().pad(5).height(3).color(Pal.accent).growX().row();
        hosts.collapser(custom, false, () -> customShown).padBottom(10).growX().row();
        
        // Online Public servers
        hosts.table(table -> {
          table.add("@claj.manage.public-servers").pad(10).padLeft(0).color(Pal.accent).growX().left();
          table.button(Icon.refresh, Styles.emptyi, this::refreshOnline).size(40f).right().padRight(3)
               .tooltip("@servers.refresh");
          table.button(Icon.downOpen, Styles.emptyi, () -> onlineShown = !onlineShown)
               .update(i -> i.getStyle().imageUp = onlineShown ? Icon.downOpen : Icon.upOpen).size(40f).right()
               .tooltip(t -> t.label(() -> "@servers." + (onlineShown ? "hide" : "show")));
        }).growX().row();
        hosts.image().pad(5).height(3).color(Pal.accent).growX().row();
        hosts.collapser(online, false, () -> onlineShown).padBottom(10).growX().row();    
      }).padRight(5).grow();
      
      // Give extra space for buttons
      inner.marginBottom(Vars.mobile ? 140f : 70f); 
    }).get().setScrollingDisabled(true, false);

    // Adds the 'Manage CLaJ room' button
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
  
  public void refreshCustom() {
    ClajServers.loadCustom();
    setupServers(ClajServers.custom, custom, true, s -> {
      ClajServers.saveCustom();
      refreshCustom(); 
    });
  }
  
  public void refreshOnline() {
    if (refreshingOnline) return;
    refreshingOnline = true;
    online.clear();
    online.button(b -> 
      b.table(t -> t.label(() -> Strings.animated(Time.time, 4, 11, ".")).color(Pal.accent)).center(), () -> {}
    ).growX().padTop(5).padBottom(5);
    
    ClajServers.refreshOnline(() -> {
      Claj.get().pingers.stop(); // cancel previous pings
      refreshingOnline = false;
      if (ClajServers.online.isEmpty()) {
        online.clear();
        online.button("@claj.servers.empty", () -> {}).growX().padTop(5).padBottom(5).row();
      } else setupServers(ClajServers.online, online, false, null);
    }, e -> {
      refreshingOnline = false;
      online.clear();
      online.button("@claj.servers.check-internet", () -> {}).growX().padTop(5).padBottom(5).row();
      Vars.ui.showException("@claj.servers.fetch-failed", e);
    }); 
  }
  
  public void setupServers(ArrayMap<String, String> servers, Table table, boolean editable, Cons<Server> deleted) {
    selected = null;// in case of
    table.clear();

    for (int i=0; i<servers.size; i++) {
      Server server = new Server();
      server.name = servers.getKeyAt(i);
      server.set(servers.getValueAt(i));
      
      Button button = new Button(); 
      button.getStyle().checkedOver = button.getStyle().checked = button.getStyle().over;
      button.setProgrammaticChangeEvents(true);
      button.clicked(() -> {
        selected = server;
        bselected = button;
      });
      table.add(button).checked(b -> bselected == b).growX().padTop(5).padBottom(5).row();

      Stack stack = new Stack();
      Table inner = new Table();
      inner.setColor(Pal.gray);
   
      button.clearChildren();
      button.add(stack).growX().row();
      
      Table ping = new Table();
      inner.add(ping).margin(0).pad(0).left().fillX();
      inner.add().expandX();
      Table label = new Table().center();
      // Cut in two line for mobiles or if the name is too long
      if (Vars.mobile || (servers.getKeyAt(i) + " (" + servers.getValueAt(i) + ')').length() > 54) {
        label.add(servers.getKeyAt(i)).pad(5, 5, 0, 5).expandX().row();
        label.add(" [lightgray](" + servers.getValueAt(i) + ')').pad(5, 0, 5, 5).expandX();
      } else label.add(servers.getKeyAt(i) + " [lightgray](" + servers.getValueAt(i) + ')') .pad(5).expandX();
      
      stack.add(label);
      stack.add(inner);
      
      if (editable) {
        final int i0 = i;
        Cell<?> edit = inner.button(Vars.mobile ? Icon.pencil : Icon.pencilSmall, Styles.emptyi, () ->
          ClajUi.add.show(server.name, server.get(), (n, a) -> {
            ClajServers.custom.setKey(i0, n);
            ClajServers.custom.setValue(i0, a);
            ClajServers.saveCustom();
            refreshCustom();          
          })
        ).right().tooltip("@server.edit");
        
        Cell<?> delete = inner.button(Vars.mobile ? Icon.trash : Icon.trashSmall, Styles.emptyi, () -> 
          Vars.ui.showConfirm("@confirm", "@server.delete", () -> {
            servers.removeKey(server.name);
            if (deleted != null) deleted.get(server);
          })
        ).right().tooltip("@server.del");
      
        if (Vars.mobile) {
          edit.size(30f).pad(2, 5, 2, 5);
          delete.size(30f).pad(2, 5, 2, 5);
        } else {
          edit.pad(4f);
          delete.pad(2f);
        }
      }

      ping.label(() -> Strings.animated(Time.time, 4, 11, ".")).pad(2).padLeft(7).color(Pal.accent).left();
      Claj.get().pingHost(server.address, server.port, s -> {
        server.compatible = s.majorVersion() == Claj.get().provider.getVersion();
        server.outdated = s.majorVersion() < Claj.get().provider.getVersion();
        
        ping.clear();
        if (server.compatible) ping.image(Icon.ok).color(Color.green).padLeft(5).padRight(5).left();
        else ping.image(Icon.warning).color(Color.yellow).pad(0, 5, 5, 5).left().get().scaleBy(-0.21f);
        if (!Vars.mobile) ping.add(s.ping() + "ms", 0.91f).color(Color.lightGray).padRight(5).left(); 
        else ping.row().add(s.ping() + "ms", 0.91f).color(Color.lightGray).padLeft(5).padRight(5).left();
      }, e -> {
        ping.clear();
        ping.image(Icon.cancel).color(Color.red).padLeft(5).padRight(5).left();
      });
    }
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
