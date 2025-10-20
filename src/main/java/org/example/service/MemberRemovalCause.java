package org.example.service;

/** Reason for removing a player from a team. */
public enum MemberRemovalCause {
  /** Player left the team voluntarily. */
  LEAVE,
  /** Player was removed forcibly by another actor. */
  KICK
}
