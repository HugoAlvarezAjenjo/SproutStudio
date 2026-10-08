package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.features.document.Document

/** Tabs with unsaved changes the window is asking about, and what to do once answered. */
class PendingClose(val docs: List<Document>, val then: () -> Unit)
