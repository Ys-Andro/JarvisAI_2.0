package com.example.jarvisai.domain.model

enum class GgufModelState {
    REGISTERED, // Imported and stored in local database, not loaded in RAM
    LOADING,    // Currently reading tensors and allocating memory
    LOADED,     // Ready in RAM for inference
    RUNNING,    // Active text generation in progress
    ERROR,      // Failed to load or execute with controlled error state
    UNLOADED    // Memory freed from RAM
}
