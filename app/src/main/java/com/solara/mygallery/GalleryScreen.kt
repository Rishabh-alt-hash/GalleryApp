package com.solara.mygallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<GalleryItem?>(null) }
    var permissionState by remember { mutableStateOf<PermissionState>(PermissionState.NotGranted) }
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.e("TAG","Playback state: $playbackState")
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.e("TAG","Buffering...")
                    Player.STATE_READY -> Log.e("TAG","Ready to play")
                    Player.STATE_ENDED -> Log.e("TAG","Playback ended")
                    Player.STATE_IDLE -> Log.e("TAG","Idle")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("TAG","Player error: ${error.message}")
                error.printStackTrace()
            }
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        when {

            // Android 14+
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {

                val fullAccess =
                    permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                            permissions[Manifest.permission.READ_MEDIA_VIDEO] == true

                val partialAccess =
                    permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true

                when {
                    fullAccess -> {
                        permissionState = PermissionState.FullAccess
                        items = loadImagesAndVideos(context)
                    }

                    partialAccess -> {
                        permissionState = PermissionState.SelectedPhotosOnly
                        items = loadImagesAndVideos(context)
                    }

                    else -> {
                        permissionState = PermissionState.NotGranted
                    }
                }
            }

            // Android 13
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {

                val granted =
                    permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                            permissions[Manifest.permission.READ_MEDIA_VIDEO] == true

                if (granted) {
                    permissionState = PermissionState.FullAccess
                    items = loadImagesAndVideos(context)
                } else {
                    permissionState = PermissionState.NotGranted
                }
            }

            // Android 12 and below
            else -> {

                val granted =
                    permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true

                if (granted) {
                    permissionState = PermissionState.FullAccess
                    items = loadImagesAndVideos(context)
                } else {
                    permissionState = PermissionState.NotGranted
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermission(permissionLauncher)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (permissionState) {
                            PermissionState.SelectedPhotosOnly -> "Gallery (Selected Photos)"
                            else -> "Gallery"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                items.isEmpty() && permissionState !is PermissionState.NotGranted -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No media found")
                    }
                }
                permissionState is PermissionState.NotGranted -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Photo/Video access required")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                checkAndRequestPermission(permissionLauncher)
                            }) {
                                Text("Request Permission")
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        items(items) { item ->
                            MediaItemThumbnail(
                                context = context,
                                item = item,
                                onClick = { selectedMedia = item }
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Modal
        if (selectedMedia != null) {
            FullscreenMediaViewer(
                item = selectedMedia!!,
                onDismiss = { selectedMedia = null }
            )
        }
    }
}

@Composable
fun MediaItemThumbnail(
    context: Context,
    item: GalleryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            val bitmap = remember(item.uri) {
                if (item.type == MediaType.Video) {
                    getVideoThumbnail(context, item.uri)
                } else null
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Video indicator
            if (item.type == MediaType.Video) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .padding(2.dp)
                ) {
                    Text(
                        "▶",
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

fun getVideoThumbnail(
    context: Context,
    videoUri: Uri
): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(
            videoUri,
            Size(300, 300),
            null
        )
    } else {
        ThumbnailUtils.createVideoThumbnail(
            File(videoUri.path ?: "").toString(),
            MediaStore.Images.Thumbnails.MINI_KIND
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaViewer(
    item: GalleryItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (item.type == MediaType.Image) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            player = exoPlayer
                            useController = true
                            keepScreenOn = true
                            controllerShowTimeoutMs = 3000
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

fun checkAndRequestPermission(launcher: ActivityResultLauncher<Array<String>>) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> { // API 34+
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> { // API 33
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        }

        else -> { // API 26-32
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }
}

fun loadImagesAndVideos(context: Context): List<GalleryItem> {
    val items = mutableListOf<GalleryItem>()

    // Load images
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        ),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val dateAdded = cursor.getLong(dateColumn)
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id
            )
            items.add(GalleryItem(
                uri = uri,
                type = MediaType.Image,
                dateAdded = dateAdded
            ))
        }
    }

    // Load videos
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        ),
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val dateAdded = cursor.getLong(dateColumn)

            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )
            items.add(GalleryItem(
                uri = uri,
                type = MediaType.Video,
                dateAdded = dateAdded
            ))
        }
    }

    return items.sortedByDescending { it.dateAdded }
}