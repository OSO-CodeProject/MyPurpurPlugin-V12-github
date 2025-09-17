package org.example;

import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.command.AdminCommands;
import org.example.command.CfgDefaultCommand;
import org.example.command.DebugToggleCommand;
import org.example.command.MenuCommand;
import org.example.command.TeamAdminCommand;
import org.example.command.TeamCommand;
import org.example.command.TeamReloadCommand;
import org.example.config.PluginConfig;
import org.example.listener.TeamChatListener;
import org.example.service.TeamManager;
import org.example.service.TeamService;

/** Главный класс плагина MyPurpurPlugin. */
public class MyPurpurPlugin extends JavaPlugin {

  // Храним как поля для возможного использования в будущем
  @SuppressWarnings("FieldCanBeLocal")
  private TeamService teamManager;

  @SuppressWarnings("FieldCanBeLocal")
  private PluginConfig pluginConfig;

  private boolean debugMode = true;

  @Override
  public void onEnable() {
    // Инициализация конфигурации
    pluginConfig = new PluginConfig(this);
    applyDebugModeFromConfig();
    // Инициализация менеджера команд
    teamManager = new TeamManager(this, pluginConfig);

    // Регистрация команд
    registerCommand("team", new TeamCommand(teamManager, pluginConfig));
    registerCommand("teamadmin", new TeamAdminCommand(teamManager, pluginConfig));
    registerCommand("getteamsuuidlist", new AdminCommands(teamManager));
    registerCommand("getteamuuid", new AdminCommands(teamManager));
    registerCommand("teamreload", new TeamReloadCommand(this, teamManager, pluginConfig));
    registerCommand("cfgDefault", new CfgDefaultCommand(this, pluginConfig, teamManager));
    registerCommand("menu", new MenuCommand(this, pluginConfig));
    registerCommand("debugtoggle", new DebugToggleCommand(this));

    // Регистрация слушателя чата
    getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager), this);

    getLogger().info("Плагин MyPurpurPlugin успешно загружен!");
  }

  /**
   * Регистрирует команду с проверкой на null.
   *
   * @param commandName Название команды
   * @param executor Экземпляр обработчика команды
   */
  private void registerCommand(String commandName, org.bukkit.command.CommandExecutor executor) {
    var command = getCommand(commandName);
    if (command == null) {
      getLogger().severe("Команда " + commandName + " не найдена в plugin.yml!");
      return;
    }
    command.setExecutor(executor);
    if (executor instanceof TabCompleter) {
      command.setTabCompleter((TabCompleter) executor);
    }
  }

  /**
   * Логирует отладочное сообщение, если включён режим отладки.
   *
   * @param message Сообщение для логирования
   */
  public void debug(String message) {
    if (debugMode) {
      getLogger().info("[DEBUG] " + message);
    }
  }

  /**
   * Логирует отладочное сообщение о действиях с командами.
   *
   * @param action Действие
   * @param playerName Имя игрока
   * @param teamName Название команды (может быть null)
   */
  public void debugTeamAction(String action, String playerName, String teamName) {
    if (debugMode) {
      String message =
          action
              + " | Игрок: "
              + (playerName != null ? playerName : "не указан")
              + " | Команда: "
              + (teamName != null ? teamName : "не указана");
      getLogger().info("[TEAM DEBUG] " + message);
    }
  }

  /**
   * Возвращает текущее состояние debugMode.
   *
   * @return true, если режим отладки включён, иначе false
   */
  @SuppressWarnings("unused")
  public boolean isDebugMode() {
    return debugMode;
  }

  /** Переключает состояние debugMode. */
  public void toggleDebugMode() {
    setDebugMode(!debugMode);
  }

  /**
   * Применяет состояние режима отладки на основе текущей конфигурации.
   */
  public void applyDebugModeFromConfig() {
    if (pluginConfig != null) {
      setDebugMode(pluginConfig.isDebugModeEnabled());
    }
  }

  /**
   * Устанавливает состояние режима отладки.
   *
   * @param enabled новое состояние режима отладки
   */
  public void setDebugMode(boolean enabled) {
    if (this.debugMode == enabled) {
      return;
    }
    this.debugMode = enabled;
    getLogger().info("Режим отладки " + (debugMode ? "включён" : "отключён") + "!");
  }

  @Override
  public void onDisable() {
    if (teamManager != null) {
      teamManager.reloadConfig();
      teamManager.shutdown();
    }
  }
}
