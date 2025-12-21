
package com.xpdustry.claj.server.plugin;

import arc.files.Fi;
import arc.util.CommandHandler;

import com.xpdustry.claj.server.ClajVars;


public abstract class Plugin {
  /** @return the folder where configuration files for this mod should go. */
  public Fi getConfigFolder() {
    return ClajVars.plugins.getConfigFolder(this);
  }
  
  /** @return the config file for this plugin, as the file 'plugins/[plugin-name]/config.json'. */
  public Fi getConfig() {
    return ClajVars.plugins.getConfig(this);
  }
  
  /** @return the meta data of this plugin .*/
  public Plugins.PluginMeta getMeta() {
    return ClajVars.plugins.getMeta(this);
  }

  /** Called after all plugins have been created and commands have been registered. */
  public void init() {}

  /** Register any commands. */
  public void registerCommands(CommandHandler handler) {}
}
