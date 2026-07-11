package com.elymbot.android.ui.persona

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.elymbot.android.ui.viewmodel.PersonaViewModel

@Composable
fun PersonaScreen(
    personaViewModel: PersonaViewModel = hiltViewModel(),
    onAddPersona: () -> Unit = {},
    onEditPersona: (String) -> Unit = {},
) = PersonaCatalogRoute(personaViewModel, onAddPersona, onEditPersona)

@Composable
fun PersonaCatalogContent(personaViewModel: PersonaViewModel = hiltViewModel()) =
    PersonaCatalogRoute(personaViewModel, {}, {})

@Composable
fun PersonaEditorScreen(personaId: String, onBack: () -> Unit, personaViewModel: PersonaViewModel = hiltViewModel()) =
    PersonaEditorRoute(personaId, personaViewModel, onBack)
