package es.hugoalvarezajenjo.sproutstudio.services.git

/** Thrown by [GitRepository] with the reason. */
class GitException(val error: GitError) : RuntimeException(error.toString())
