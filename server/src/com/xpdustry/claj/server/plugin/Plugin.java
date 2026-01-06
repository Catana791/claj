
package com.xpdustry.claj.server.plugin;

import arc.files.Fi;
import arc.util.CommandHandler;

import com.xpdustry.claj.server.ClajVars;
import com.xpdustry.claj.server.util.JsonSettings;


public abstract class Plugin {
  /** @return a new logger for this plugin. The plugin is automatically determined using the caller class. */
  public static PluginLogger getLogger() {
    return ClajVars.plugins.getLogger();
  }
  
  /** 
   * @return a new logger for this plugin with the specified {@code topicClass}.
   *         The plugin is automatically determined using the caller class. 
   */
  public static PluginLogger getLogger(Class<?> topicClass) {
    return ClajVars.plugins.getLogger(topicClass);
  }
  
  /** 
   * @return a new logger for this plugin with the specified {@code topic}.
   *         The plugin is automatically determined using the caller class. 
   */
  public static PluginLogger getLogger(String topic) {
    return ClajVars.plugins.getLogger(topic);
  }  
  
  /** @return the folder where configuration files for this mod should go. */
  public Fi getConfigFolder() {
    return ClajVars.plugins.getConfigFolder(this);
  }
  
  /** @return the config file for this plugin, as the file 'plugins/[plugin-name]/config.json'. */
  public JsonSettings getConfig() {
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
