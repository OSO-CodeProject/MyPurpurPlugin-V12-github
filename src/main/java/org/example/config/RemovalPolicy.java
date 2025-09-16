package org.example.config;

/** Возможные стратегии удаления лишних игроков из команды. */
public enum RemovalPolicy {
  OFFLINE_FIRST,
  LAST_JOINED
}
