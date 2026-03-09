package org.example.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.example.model.PendingInvite;
import org.example.model.PendingRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Утилитарный класс для работы с сообщениями команд. */
public class TeamMessageUtils {

  /**
   * Отправляет сообщение игроку.
   *
   * @param player Игрок, которому отправляется сообщение
   * @param message Сообщение для отправки
   */
  public static void sendTeamMessage(Player player, Component message) {
    if (player != null) {
      player.sendMessage(message);
    }
  }

  /**
   * Формирует сообщение об ошибке, если команда уже существует.
   *
   * @param teamName Название команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component teamAlreadyExistsMessage(String teamName) {
    return Component.text("❌ Команда ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" уже существует!", NamedTextColor.RED));
  }

  /**
   * Формирует сообщение об ошибке, если команда не существует.
   *
   * @param teamName Название команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component teamDoesNotExistMessage(String teamName) {
    String name = (teamName != null) ? teamName : "неизвестная команда";
    return Component.text("❌ Команда ", NamedTextColor.RED)
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(" не существует!", NamedTextColor.RED));
  }

  /**
   * Формирует сообщение о том, что игрок не состоит ни в одной команде.
   *
   * @return сообщение об ошибке
   */
  public static Component playerNotInAnyTeamMessage() {
    return Component.text("❌ Вы не состоите в команде!", NamedTextColor.RED);
  }

  /**
   * Формирует сообщение о том, что действие доступно только лидеру команды.
   *
   * @return сообщение об ошибке
   */
  public static Component notTeamLeaderMessage() {
    return Component.text("❌ Это действие доступно только лидеру команды!", NamedTextColor.RED);
  }

  /**
   * Формирует сообщение об ошибке, если запрошенный игрок не найден.
   *
   * @param playerName имя игрока
   * @return сообщение об ошибке
   */
  public static Component playerNotFoundMessage(String playerName) {
    return Component.text("❌ Игрок ", NamedTextColor.RED)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" не найден!", NamedTextColor.RED));
  }

  /** Возвращает сообщение о том, что команды приглашений сейчас недоступны. */
  public static Component invitesDisabledMessage() {
    return Component.text(
        "❌ Управление приглашениями доступно только в режиме приглашений.", NamedTextColor.RED);
  }

  /** Сообщение о том, что нельзя пригласить самого себя. */
  public static Component cannotInviteSelfMessage() {
    return Component.text("❌ Нельзя пригласить самого себя!", NamedTextColor.RED);
  }

  /** Уведомление о том, что цель уже состоит в команде. */
  public static Component targetAlreadyInTeamMessage(String playerName, String teamName) {
    return Component.text("❌ Игрок ", NamedTextColor.RED)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" уже состоит в команде ", NamedTextColor.RED))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.RED));
  }

  /** Сообщение об ошибочном формате времени приглашения. */
  public static Component invalidInviteDurationMessage() {
    return Component.text(
        "❌ Укажите неотрицательное целое число минут для времени действия приглашения.",
        NamedTextColor.RED);
  }

  /** Сообщение о том, что команда заполнена. */
  public static Component teamIsFullMessage(String teamName) {
    return Component.text("❌ Команда ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" заполнена.", NamedTextColor.RED));
  }

  /** Сообщает лидеру, что приглашение уже отправлено. */
  public static Component inviteAlreadyExistsMessage(String playerName, String teamName) {
    return Component.text("ℹ️ У игрока ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" уже есть приглашение в команду ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /** Создаёт сообщение о получении приглашения с интерактивными действиями. */
  public static Component inviteReceivedMessage(
      String teamName,
      String inviterName,
      @Nullable Long expiresAt,
      String acceptCommand,
      String declineCommand) {
    Component message =
        Component.text("📨 Вас пригласили в команду ", NamedTextColor.AQUA)
            .append(Component.text(teamName, NamedTextColor.GOLD))
            .append(Component.text(" от ", NamedTextColor.AQUA))
            .append(Component.text(inviterName, NamedTextColor.GOLD))
            .append(expiryInfo(expiresAt));
    Component actions =
        Component.space()
            .append(
                clickableAction(
                    "[Принять]", NamedTextColor.GREEN, acceptCommand, "Принять приглашение"))
            .append(Component.space())
            .append(
                clickableAction(
                    "[Отклонить]", NamedTextColor.RED, declineCommand, "Отклонить приглашение"));
    return message.append(actions);
  }

  /** Сообщает лидеру об успешной отправке приглашения. */
  public static Component inviteSentLeaderMessage(String playerName, @Nullable Long expiresAt) {
    return Component.text("📨 Приглашение отправлено игроку ", NamedTextColor.GREEN)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(expiryInfo(expiresAt));
  }

  /** Сообщение для участников команды о новом приглашении. */
  public static Component inviteSentTeamBroadcastMessage(String playerName, String inviterName) {
    return Component.text("📨 Лидер ", NamedTextColor.AQUA)
        .append(Component.text(inviterName, NamedTextColor.WHITE))
        .append(Component.text(" пригласил ", NamedTextColor.AQUA))
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" в команду.", NamedTextColor.AQUA));
  }

  /** Сообщение для администраторов о новом приглашении. */
  public static Component inviteSentAdminMessage(String inviter, String target, String teamName) {
    return Component.text("📨 ", NamedTextColor.YELLOW)
        .append(Component.text(inviter, NamedTextColor.WHITE))
        .append(Component.text(" пригласил ", NamedTextColor.YELLOW))
        .append(Component.text(target, NamedTextColor.WHITE))
        .append(Component.text(" в команду ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /**
   * Сообщает, что команда принимает игроков только по приглашению.
   *
   * @return информационное сообщение
   */
  public static Component joinInviteOnlyMessage() {
    return Component.text(
        "ℹ️ Эта команда принимает новых участников только по приглашению лидера.",
        NamedTextColor.YELLOW);
  }

  /**
   * Сообщает игроку, что заявка на вступление отправлена лидеру.
   *
   * @param teamName название команды
   * @return информационное сообщение
   */
  public static Component joinRequestSentMessage(String teamName) {
    return joinRequestSentPlayerMessage(teamName);
  }

  /** Уведомляет игрока о том, что заявка на вступление отправлена. */
  public static Component joinRequestSentPlayerMessage(String teamName) {
    String name = (teamName == null || teamName.isBlank()) ? "команду" : teamName;
    return Component.text("ℹ️ Заявка на вступление в команду ", NamedTextColor.YELLOW)
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(" отправлена лидеру.", NamedTextColor.YELLOW));
  }

  /**
   * Сообщает игроку, что заявка на вступление уже существует.
   *
   * @param teamName название команды
   * @return информационное сообщение
   */
  public static Component joinRequestAlreadySentMessage(String teamName) {
    String name = (teamName == null || teamName.isBlank()) ? "команду" : teamName;
    return Component.text("ℹ️ Вы уже отправили заявку в команду ", NamedTextColor.YELLOW)
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /**
   * Уведомляет лидера о новой заявке на вступление.
   *
   * @param playerName имя игрока, отправившего заявку
   * @param teamName название команды
   * @return информационное сообщение
   */
  public static Component joinRequestReceivedLeaderMessage(String playerName, String teamName) {
    String candidate =
        (playerName == null || playerName.isBlank()) ? "Неизвестный игрок" : playerName;
    String name = (teamName == null || teamName.isBlank()) ? "вашу команду" : teamName;
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(candidate, NamedTextColor.WHITE))
        .append(Component.text(" хочет вступить в команду ", NamedTextColor.YELLOW))
        .append(Component.text(name, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /** Сообщает игроку об отсутствии активной заявки. */
  public static Component joinRequestNotFoundMessage(String teamName) {
    return Component.text("❌ Активная заявка в команду ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" не найдена.", NamedTextColor.RED));
  }

  /** Сообщение для лидера о том, что заявка не найдена. */
  public static Component joinRequestNotFoundLeaderMessage(String playerName) {
    return Component.text("❌ Заявка от игрока ", NamedTextColor.RED)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" не найдена.", NamedTextColor.RED));
  }

  /** Сообщает лидеру, что игрок не в сети для утверждения заявки. */
  public static Component joinRequestTargetOfflineMessage(String playerName) {
    return Component.text("❌ Игрок ", NamedTextColor.RED)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" не в сети.", NamedTextColor.RED));
  }

  /** Сообщает игроку об одобрении заявки лидером. */
  public static Component joinRequestApprovedPlayerMessage(String teamName) {
    return Component.text("✅ Заявка в команду ", NamedTextColor.GREEN)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" одобрена!", NamedTextColor.GREEN));
  }

  /** Сообщает лидеру об успешном одобрении заявки. */
  public static Component joinRequestApprovedLeaderMessage(String playerName, String teamName) {
    return Component.text("✅ Вы одобрили заявку игрока ", NamedTextColor.GREEN)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" в команду ", NamedTextColor.GREEN))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.GREEN));
  }

  /** Сообщение игроку об отклонении заявки. */
  public static Component joinRequestDeniedPlayerMessage(String teamName) {
    return Component.text("❌ Заявка в команду ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" отклонена.", NamedTextColor.RED));
  }

  /** Сообщение лидеру об отклонении заявки. */
  public static Component joinRequestDeniedLeaderMessage(
      String playerName, String teamName, @Nullable String actorName) {
    Component base =
        Component.text("ℹ️ Заявка игрока ", NamedTextColor.YELLOW)
            .append(Component.text(playerName, NamedTextColor.WHITE))
            .append(Component.text(" в команду ", NamedTextColor.YELLOW))
            .append(Component.text(teamName, NamedTextColor.WHITE))
            .append(Component.text(" отклонена", NamedTextColor.YELLOW));
    if (actorName != null && !actorName.isBlank()) {
      base =
          base.append(Component.text(" игроком ", NamedTextColor.YELLOW))
              .append(Component.text(actorName, NamedTextColor.WHITE));
    }
    return base.append(Component.text(".", NamedTextColor.YELLOW));
  }

  /** Сообщение игроку об отзыве собственной заявки. */
  public static Component joinRequestCancelledPlayerMessage(String teamName) {
    return Component.text("ℹ️ Заявка в команду ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" отозвана.", NamedTextColor.YELLOW));
  }

  /** Уведомляет лидера об отзыве заявки игроком. */
  public static Component joinRequestCancelledLeaderMessage(
      String playerName, @Nullable String actorName) {
    String displayName = actorName != null ? actorName : playerName;
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(displayName, NamedTextColor.WHITE))
        .append(Component.text(" отозвал свою заявку.", NamedTextColor.YELLOW));
  }

  /** Сообщение игроку о том, что заявка истекла или команда распущена. */
  public static Component joinRequestExpiredPlayerMessage(String teamName) {
    return Component.text("ℹ️ Заявка в команду ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" больше не действует.", NamedTextColor.YELLOW));
  }

  /** Сообщение лидеру об автоматическом удалении заявки. */
  public static Component joinRequestExpiredLeaderMessage(String playerName, String teamName) {
    return Component.text("ℹ️ Заявка игрока ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" в команду ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" истекла.", NamedTextColor.YELLOW));
  }

  /** Сообщает лидеру, что заявка очищена автоматически после вступления. */
  public static Component joinRequestAutoClearedLeaderMessage(String playerName, String teamName) {
    return Component.text("ℹ️ Заявка игрока ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" в команду ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" закрыта автоматически.", NamedTextColor.YELLOW));
  }

  /** Заголовок списка заявок для игрока. */
  public static Component joinRequestsHeaderMessage() {
    return Component.text("📨 Ваши заявки:", NamedTextColor.AQUA);
  }

  /** Заголовок списка заявок для лидера команды. */
  public static Component joinRequestsTeamHeaderMessage(String teamName) {
    return Component.text("📨 Заявки на вступление в команду ", NamedTextColor.AQUA)
        .append(Component.text(teamName, NamedTextColor.GOLD))
        .append(Component.text(":", NamedTextColor.AQUA));
  }

  /** Пункт списка заявок с кнопкой для отмены. */
  public static Component joinRequestListEntry(
      @NotNull PendingRequest request, String cancelCommand) {
    Component entry =
        Component.text("• ", NamedTextColor.GRAY)
            .append(Component.text(request.getTeamName(), NamedTextColor.GOLD))
            .append(Component.text(" (", NamedTextColor.GRAY))
            .append(Component.text(request.getPlayerName(), NamedTextColor.WHITE))
            .append(Component.text(")", NamedTextColor.GRAY));
    if (request.getExpiresAt() != null) {
      entry = entry.append(expiryInfo(request.getExpiresAt()));
    }
    return entry
        .append(Component.space())
        .append(
            clickableAction("[Отозвать]", NamedTextColor.RED, cancelCommand, "Отозвать заявку"));
  }

  /** Элемент списка заявок для лидера с кнопками одобрения и отказа. */
  public static Component joinRequestTeamListEntry(
      @NotNull PendingRequest request, String acceptCommand, String denyCommand) {
    Component entry =
        Component.text("• ", NamedTextColor.GRAY)
            .append(Component.text(request.getPlayerName(), NamedTextColor.WHITE));
    if (request.getExpiresAt() != null) {
      entry = entry.append(expiryInfo(request.getExpiresAt()));
    }
    return entry
        .append(Component.space())
        .append(
            clickableAction("[Одобрить]", NamedTextColor.GREEN, acceptCommand, "Одобрить заявку"))
        .append(Component.space())
        .append(
            clickableAction("[Отклонить]", NamedTextColor.RED, denyCommand, "Отклонить заявку"));
  }

  /** Сообщение для лидера после очистки всех заявок. */
  public static Component joinRequestsClearedLeaderMessage(int count) {
    return Component.text("ℹ️ Очищено ", NamedTextColor.YELLOW)
        .append(Component.text(count, NamedTextColor.WHITE))
        .append(Component.text(" заявок.", NamedTextColor.YELLOW));
  }

  /** Сообщение об отсутствии заявок. */
  public static Component noJoinRequestsMessage() {
    return Component.text("ℹ️ У вас нет активных заявок.", NamedTextColor.YELLOW);
  }

  /**
   * Формирует сообщение об ошибке, если игрок уже состоит в команде.
   *
   * @param teamName Название команды
   * @param prefixComponent Префикс команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component playerAlreadyInTeamMessage(String teamName, Component prefixComponent) {
    return Component.text("❌ Вы уже состоите в команде ", NamedTextColor.RED)
        .append(prefixComponent)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text("!", NamedTextColor.RED));
  }

  /**
   * Формирует сообщение об ошибке, если игрок не состоит в команде.
   *
   * @param teamName Название команды
   * @param prefixComponent Префикс команды
   * @return Форматированное сообщение об ошибке
   */
  public static Component playerNotInTeamMessage(String teamName, Component prefixComponent) {
    return Component.text("❌ Вы не состоите в команде ", NamedTextColor.RED)
        .append(prefixComponent)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text("!", NamedTextColor.RED));
  }

  /** Сообщает лидеру, что приглашение для указанного игрока не найдено. */
  public static Component inviteNotFoundForLeaderMessage(String playerName, String teamName) {
    return Component.text("❌ Приглашение для ", NamedTextColor.RED)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" в команду ", NamedTextColor.RED))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" не найдено.", NamedTextColor.RED));
  }

  /** Сообщает игроку, что у него нет активного приглашения от команды. */
  public static Component inviteNotFoundForPlayerMessage(String teamName) {
    return Component.text("❌ Активное приглашение от команды ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" не найдено.", NamedTextColor.RED));
  }

  /** Подтверждает игроку отзыв приглашения. */
  public static Component inviteRevokedLeaderMessage(String playerName) {
    return Component.text("ℹ️ Приглашение для ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" отозвано.", NamedTextColor.YELLOW));
  }

  /** Информирует цель об отзыве приглашения. */
  public static Component inviteRevokedTargetMessage(String teamName) {
    return Component.text("ℹ️ Приглашение команды ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" было отозвано.", NamedTextColor.YELLOW));
  }

  /** Оповещает администраторов о том, что приглашение было отозвано. */
  public static Component inviteRevokedAdminMessage(
      String inviter, String target, String teamName) {
    return Component.text("📨 ", NamedTextColor.YELLOW)
        .append(Component.text(inviter, NamedTextColor.WHITE))
        .append(Component.text(" отозвал приглашение для ", NamedTextColor.YELLOW))
        .append(Component.text(target, NamedTextColor.WHITE))
        .append(Component.text(" в команду ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /** Уведомляет игрока о принятии приглашения. */
  public static Component inviteAcceptedMessage(String teamName) {
    return Component.text("✅ Вы приняли приглашение команды ", NamedTextColor.GREEN)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text("!", NamedTextColor.GREEN));
  }

  /** Сообщение об использовании команды принятия приглашения. */
  public static Component acceptInviteUsageMessage() {
    return Component.text("❌ Использование: /team accept <команда>", NamedTextColor.RED);
  }

  /** Сообщает участникам команды об успешном вступлении приглашённого. */
  public static Component inviteAcceptedBroadcastMessage(String playerName) {
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" принял приглашение и вступил в команду.", NamedTextColor.YELLOW));
  }

  /** Сообщение для администраторов о принятии приглашения. */
  public static Component inviteAcceptedAdminMessage(
      String inviter, String target, String teamName) {
    return Component.text("📨 ", NamedTextColor.YELLOW)
        .append(Component.text(target, NamedTextColor.WHITE))
        .append(Component.text(" принял приглашение команды ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" от ", NamedTextColor.YELLOW))
        .append(Component.text(inviter, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /** Сообщает игроку, что приглашение было отклонено. */
  public static Component inviteDeclinedMessage(String teamName) {
    return Component.text("ℹ️ Приглашение команды ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" отклонено.", NamedTextColor.YELLOW));
  }

  /** Сообщение об использовании команды отклонения приглашения. */
  public static Component declineInviteUsageMessage() {
    return Component.text("❌ Использование: /team decline <команда>", NamedTextColor.RED);
  }

  /** Броадкаст для команды о том, что приглашение отклонено. */
  public static Component inviteDeclinedBroadcastMessage(String playerName) {
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" отклонил приглашение в команду.", NamedTextColor.YELLOW));
  }

  /** Сообщение для администраторов об отклонении приглашения. */
  public static Component inviteDeclinedAdminMessage(
      String inviter, String target, String teamName) {
    return Component.text("📨 ", NamedTextColor.YELLOW)
        .append(Component.text(target, NamedTextColor.WHITE))
        .append(Component.text(" отклонил приглашение команды ", NamedTextColor.YELLOW))
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" от ", NamedTextColor.YELLOW))
        .append(Component.text(inviter, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /** Сообщение о том, что срок действия приглашения истёк. */
  public static Component inviteExpiredMessage(String teamName) {
    return Component.text("❌ Срок действия приглашения команды ", NamedTextColor.RED)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" истёк.", NamedTextColor.RED));
  }

  /** Сообщает игроку об отсутствии активных приглашений. */
  public static Component noInvitesMessage() {
    return Component.text("ℹ️ У вас нет активных приглашений.", NamedTextColor.YELLOW);
  }

  /** Заголовок списка приглашений. */
  public static Component invitesHeaderMessage() {
    return Component.text("📨 Ваши приглашения:", NamedTextColor.AQUA);
  }

  /** Строка списка приглашений с интерактивными действиями. */
  public static Component inviteListEntry(
      @NotNull PendingInvite invite, String acceptCommand, String declineCommand) {
    Component entry =
        Component.text("• ", NamedTextColor.GRAY)
            .append(Component.text(invite.getTeamName(), NamedTextColor.GOLD))
            .append(Component.text(" (", NamedTextColor.GRAY))
            .append(Component.text(invite.getInviterName(), NamedTextColor.WHITE))
            .append(Component.text(")", NamedTextColor.GRAY))
            .append(expiryInfo(invite.getExpiresAt()));
    Component actions =
        Component.space()
            .append(
                clickableAction(
                    "[Принять]", NamedTextColor.GREEN, acceptCommand, "Принять приглашение"))
            .append(Component.space())
            .append(
                clickableAction(
                    "[Отклонить]", NamedTextColor.RED, declineCommand, "Отклонить приглашение"));
    return entry.append(actions);
  }

  /**
   * Формирует предупреждение о необходимости сократить команду.
   *
   * @param max новый лимит участников
   * @param minutes время на сокращение
   * @param excess количество лишних участников
   * @return сообщение-предупреждение
   */
  public static Component deadlineWarningMessage(int max, int minutes, int excess) {
    return Component.text("Максимум игроков уменьшен до ", NamedTextColor.YELLOW)
        .append(Component.text(max, NamedTextColor.WHITE))
        .append(Component.text(". У вас ", NamedTextColor.YELLOW))
        .append(Component.text(minutes + " мин", NamedTextColor.WHITE))
        .append(Component.text(", чтобы исключить ", NamedTextColor.YELLOW))
        .append(Component.text(excess + " участника(ов)", NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  /**
   * Сообщение после принудительного удаления участников.
   *
   * @param removed количество удалённых участников
   * @return сообщение для лидера
   */
  public static Component forcedRemovalMessage(int removed) {
    return Component.text("Из вашей команды удалено ", NamedTextColor.RED)
        .append(Component.text(removed + " участника(ов)", NamedTextColor.WHITE))
        .append(Component.text(" из-за превышения лимита.", NamedTextColor.RED));
  }

  /**
   * Сообщение с оставшимся временем для удаления лишних участников.
   *
   * @param minutes оставшиеся минуты
   * @param seconds оставшиеся секунды
   * @param excess количество лишних участников
   * @return сообщение для лидера
   */
  public static Component deadlineRemainingMessage(long minutes, long seconds, int excess) {
    return Component.text("Осталось ", NamedTextColor.YELLOW)
        .append(Component.text(String.format("%d:%02d", minutes, seconds), NamedTextColor.WHITE))
        .append(Component.text(" чтобы исключить ", NamedTextColor.YELLOW))
        .append(Component.text(excess + " участника(ов)", NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  private static Component expiryInfo(@Nullable Long expiresAt) {
    if (expiresAt == null) {
      return Component.text(" (без срока действия)", NamedTextColor.GRAY);
    }
    long remaining = expiresAt - System.currentTimeMillis();
    if (remaining <= 0) {
      return Component.text(" (истёк)", NamedTextColor.RED);
    }
    return Component.text(
        " (истекает через " + formatDuration(remaining) + ")", NamedTextColor.GRAY);
  }

  private static String formatDuration(long millis) {
    long totalSeconds = Math.max(0L, millis / 1000L);
    long hours = totalSeconds / 3600L;
    long minutes = (totalSeconds % 3600L) / 60L;
    long seconds = totalSeconds % 60L;
    StringBuilder builder = new StringBuilder();
    if (hours > 0) {
      builder.append(hours).append(" ч");
    }
    if (minutes > 0 || hours > 0) {
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(minutes).append(" мин");
    }
    if (seconds > 0 || builder.length() == 0) {
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(seconds).append(" сек");
    }
    return builder.toString();
  }

  private static Component clickableAction(
      String label, NamedTextColor color, String command, String hoverText) {
    return Component.text(label, color)
        .decorate(TextDecoration.BOLD)
        .clickEvent(ClickEvent.runCommand(command))
        .hoverEvent(HoverEvent.showText(Component.text(hoverText, color)));
  }

  public static Component teamDisbandedLeaderMessage(String teamName) {
    return Component.text("✅ Команда ", NamedTextColor.GREEN)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(" распущена.", NamedTextColor.GREEN));
  }

  public static Component teamDisbandedMemberMessage(String teamName, String leaderName) {
    Component base =
        Component.text("ℹ️ Команда ", NamedTextColor.YELLOW)
            .append(Component.text(teamName, NamedTextColor.WHITE))
            .append(Component.text(" была распущена", NamedTextColor.YELLOW));
    if (leaderName != null && !leaderName.isBlank()) {
      base =
          base.append(Component.text(" лидером ", NamedTextColor.YELLOW))
              .append(Component.text(leaderName, NamedTextColor.WHITE));
    }
    return base.append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component leadershipTransferOutgoingMessage(String newLeaderName) {
    return Component.text("✅ Вы передали лидерство игроку ", NamedTextColor.GREEN)
        .append(Component.text(newLeaderName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.GREEN));
  }

  public static Component leadershipTransferIncomingMessage(String teamName) {
    return Component.text("ℹ️ Вы стали лидером команды ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component leadershipTransferBroadcastMessage(String newLeaderName) {
    return Component.text("ℹ️ Новый лидер команды — ", NamedTextColor.YELLOW)
        .append(Component.text(newLeaderName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component memberKickedLeaderMessage(String targetName) {
    return Component.text("✅ Игрок ", NamedTextColor.GREEN)
        .append(Component.text(targetName, NamedTextColor.WHITE))
        .append(Component.text(" исключён из команды.", NamedTextColor.GREEN));
  }

  public static Component memberKickedTargetMessage(String teamName, String leaderName) {
    Component base =
        Component.text("❌ Вас исключили из команды ", NamedTextColor.RED)
            .append(Component.text(teamName, NamedTextColor.WHITE));
    if (leaderName != null && !leaderName.isBlank()) {
      base =
          base.append(Component.text(" лидером ", NamedTextColor.RED))
              .append(Component.text(leaderName, NamedTextColor.WHITE));
    }
    return base.append(Component.text(".", NamedTextColor.RED));
  }

  public static Component memberKickedBroadcastMessage(String targetName) {
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(targetName, NamedTextColor.WHITE))
        .append(Component.text(" был исключён из команды.", NamedTextColor.YELLOW));
  }

  public static Component memberLeftSelfMessage(String teamName) {
    return Component.text("ℹ️ Вы покинули команду ", NamedTextColor.YELLOW)
        .append(Component.text(teamName, NamedTextColor.WHITE))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component memberLeftBroadcastMessage(String playerName) {
    return Component.text("ℹ️ Игрок ", NamedTextColor.YELLOW)
        .append(Component.text(playerName, NamedTextColor.WHITE))
        .append(Component.text(" покинул команду.", NamedTextColor.YELLOW));
  }

  public static Component teamPrefixUpdatedLeaderMessage(String prefix) {
    return Component.text("✅ Префикс команды обновлён", NamedTextColor.GREEN)
        .append(Component.text(prefixDisplay(prefix), NamedTextColor.WHITE));
  }

  public static Component teamPrefixUpdatedMemberMessage(String prefix) {
    return Component.text("ℹ️ Префикс команды обновлён", NamedTextColor.YELLOW)
        .append(Component.text(prefixDisplay(prefix), NamedTextColor.WHITE));
  }

  public static Component teamRenamedLeaderMessage(String oldName, String newName) {
    return Component.text("✅ Команда ", NamedTextColor.GREEN)
        .append(nameComponent(oldName))
        .append(Component.text(" переименована в ", NamedTextColor.GREEN))
        .append(nameComponent(newName))
        .append(Component.text(".", NamedTextColor.GREEN));
  }

  public static Component teamRenamedMemberMessage(String oldName, String newName) {
    return Component.text("ℹ️ Команда ", NamedTextColor.YELLOW)
        .append(nameComponent(oldName))
        .append(Component.text(" переименована в ", NamedTextColor.YELLOW))
        .append(nameComponent(newName))
        .append(Component.text(".", NamedTextColor.YELLOW));
  }

  public static Component teamColorUpdatedLeaderMessage(String color) {
    return Component.text("✅ Цвет команды обновлён", NamedTextColor.GREEN)
        .append(Component.text(valueDisplay(color), NamedTextColor.WHITE));
  }

  public static Component teamColorUpdatedMemberMessage(String color) {
    return Component.text("ℹ️ Цвет команды обновлён", NamedTextColor.YELLOW)
        .append(Component.text(valueDisplay(color), NamedTextColor.WHITE));
  }

  private static String prefixDisplay(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      return " (пустой)";
    }
    return " — \"" + prefix + "\"";
  }

  private static String valueDisplay(String value) {
    if (value == null || value.isEmpty()) {
      return " (пустое значение)";
    }
    return " — \"" + value + "\"";
  }

  private static Component nameComponent(String name) {
    String value = (name == null || name.isEmpty()) ? "(без названия)" : name;
    return Component.text(value, NamedTextColor.WHITE);
  }
}
