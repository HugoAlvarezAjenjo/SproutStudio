package es.hugoalvarezajenjo.sproutstudio.services.git

/** How a file differs from the last commit (HEAD), the way IntelliJ's Commit window shows it. */
enum class ChangeType { MODIFIED, ADDED, DELETED, UNVERSIONED, CONFLICT }
