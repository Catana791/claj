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
import arc.input.KeyCode;
import arc.util.Strings;
import arc.util.Time;

import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.BaseDialog;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.common.status.RejectReason;


public class JoinViaClajDialog extends BaseDialog {
  String lastLink = "claj://";
  boolean valid;
  String output;

  public JoinViaClajDialog() {
    super("@claj.join.name");

    cont.defaults().width(Vars.mobile ? 350f : 550f);
    
    cont.labelWrap("@claj.join.note").padBottom(10f).left().row();
    cont.table(table -> {
      table.add("@claj.join.link").padRight(5f).left();
      table.field(lastLink, this::setLink).maxTextLength(100).valid(this::setLink).height(54f).growX().row();
      table.add();
      table.labelWrap(() -> output).left().growX().row();
    }).row();

    buttons.defaults().size(140f, 60f).pad(4f);
    buttons.button("@cancel", this::hide);
    buttons.button("@ok", this::joinRoom).disabled(button -> !valid || lastLink.isEmpty() || Vars.net.active());
    
    addCloseListener();
    keyDown(KeyCode.enter, () -> {
      if (!valid || lastLink.isEmpty() || Vars.net.active()) return;
      joinRoom();
    });
    
    //Adds the 'Join via CLaJ' button
    if (!Vars.steam && !Vars.mobile) {
      Vars.ui.join.buttons.button("@claj.join.name", Icon.play, this::show);
      Vars.ui.join.buttons.getCells().insert(4, Vars.ui.join.buttons.getCells().pop());
    } else {
      // adds in a new line for mobile players
      Vars.ui.join.buttons.row().add().growX().width(-1);
      Vars.ui.join.buttons.button("@claj.join.name", Icon.play, this::show);
    }
  }

  public void joinRoom() {
    if (Vars.player.name.trim().isEmpty()) {
      Vars.ui.showInfo("@noname");
      return;
    }
    
    ClajLink link;
    try { link = ClajLink.fromString(lastLink); } 
    catch (Exception e) {
      valid = false;
      Vars.ui.showErrorMessage(Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage());
      return;
    }

    Vars.ui.loadfrag.show("@connecting");
    Vars.ui.loadfrag.setButton(() -> {
      Vars.ui.loadfrag.hide();
      Vars.netClient.disconnectQuietly();
    });
    
    Time.runTask(2f, () -> 
      Claj.get().joinRoom(link, () -> {
        Vars.ui.join.hide();
        hide();
      }, r -> {
        switch (r) {
          default:
            //TODO: show password          
          case serverClosing:
            hide(); 
          case roomNotFound:
            if (r != RejectReason.passwordRequired)
              Vars.ui.showErrorMessage("@claj.reject." + Strings.camelToKebab(r.name()));
        }
      }, Vars.net::handleException)
    );
  }
  
  public boolean setLink(String link) {
    if (lastLink.equals(link)) return valid;

    lastLink = link;
    try { 
      ClajLink.fromString(lastLink); 
      output = "@claj.join.valid";
      return valid = true;
      
    } catch (Exception e) {
      output = Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage();
      return valid = false;
    }
  }
}
