package es.hugoalvarezajenjo.sproutstudio.services.git

/** Who commits will be made as; [implicit] when git had to guess (no user.name/email set). */
data class GitAuthor(val name: String, val email: String, val implicit: Boolean)
