package es.hugoalvarezajenjo.sproutstudio.services.render

/** An export or copy was refused: the diagram has a remote `!include` at [line] (the app is offline). */
class OfflineIncludeException(val line: Int) : IllegalStateException("Remote include on line $line")
