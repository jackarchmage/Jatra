package com.jks.jatrav3

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jks.jatrav3.api.JatraRepository
import com.jks.jatrav3.api.SuperArUser
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * ARViewModel - handles fetching AR files from API and exposes them as LiveData
 */
class ARViewModel : ViewModel() {

    private val _state = MutableLiveData<UiState>()
    val state: LiveData<UiState> = _state

    private val repo = JatraRepository()

    fun loadArFiles(customerId: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val response = repo.fetchArFiles(customerId)
                _state.value = UiState.Success(response.super_ar_user)
            } catch (e: IOException) {
                _state.value = UiState.Error("Network error: ${e.message}")
            } catch (e: HttpException) {
                _state.value = UiState.Error("Server error: ${e.code()}")
            } catch (e: Exception) {
                _state.value = UiState.Error("Unexpected error: ${e.message}")
            }
        }
    }
}

/**
 * UI state for AR list
 */
sealed class UiState {
    object Loading : UiState()
    data class Success(val items: List<SuperArUser>) : UiState()
    data class Error(val message: String) : UiState()
}
