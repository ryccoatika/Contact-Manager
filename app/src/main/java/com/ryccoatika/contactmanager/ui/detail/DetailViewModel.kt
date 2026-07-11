package com.ryccoatika.contactmanager.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.domain.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailEvent {
    data object NavigateBack : DetailEvent
    data class ShowMessage(val message: String) : DetailEvent
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    contactsSource: ContactsSource,
    private val writer: ContactsWriter,
) : ViewModel() {

    private val contactId: Long = checkNotNull(savedStateHandle["contactId"])

    val uiState: StateFlow<Contact?> = contactsSource.observeContacts()
        .map { contacts -> contacts.find { it.contactId == contactId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<DetailEvent>()
    val events: SharedFlow<DetailEvent> = _events

    fun deleteRawContact(rawContactId: Long) {
        val wasLastRawContact = uiState.value?.rawContacts?.size == 1
        viewModelScope.launch {
            when (val result = writer.deleteRawContacts(listOf(rawContactId))) {
                is ContactOpResult.Success ->
                    if (wasLastRawContact) _events.emit(DetailEvent.NavigateBack)
                is ContactOpResult.Failure -> _events.emit(DetailEvent.ShowMessage(result.message))
            }
        }
    }
}
