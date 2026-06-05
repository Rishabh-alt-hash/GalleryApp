package com.solara.mygallery

import android.net.Uri

sealed class PermissionState {
    data object NotGranted : PermissionState()
    data object FullAccess : PermissionState()
    data object SelectedPhotosOnly : PermissionState()
}

sealed class MediaType {
    data object Image : MediaType()
    data object Video : MediaType()
}

data class GalleryItem(
    val uri: Uri,
    val type: MediaType,
    val dateAdded: Long = 0
)