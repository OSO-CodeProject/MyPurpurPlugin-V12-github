package org.example.service;

/** Represents the outcome of a team rename operation. */
public enum RenameResult {
  /** Rename completed successfully. */
  SUCCESS,
  /** The requested name is already used by another team. */
  NAME_TAKEN,
  /** The target team could not be found. */
  TEAM_NOT_FOUND,
  /** The requesting player is not allowed to rename the team. */
  NOT_LEADER;
}
