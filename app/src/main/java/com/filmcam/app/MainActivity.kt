package com.filmcam.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    FilmCamApp()
                }
            }
        }
    }
}

@Composable
fun FilmCamApp() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraScreen()
    } else {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("카메라 권한이 필요해요", color = Color.White)
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("권한 허용") }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    var glView by remember { mutableStateOf<CameraGLView?>(null) }
    var glToken by remember { mutableIntStateOf(0) }
    var front by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(1) } // 기본: 버터
    var strength by remember { mutableFloatStateOf(1f) }
    var comparing by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    val adjust = remember {
        mutableStateListOf<Float>().apply { repeat(Adjust.values().size) { add(0f) } }
    }

    val preset = FilterPresets.all[selected]
    val params = preset.params.withAdjust(adjust)

    // 필터 값이 바뀔 때마다 GL 쪽으로 전달 (길게 누르는 동안은 원본 비교)
    LaunchedEffect(glView, params, strength, comparing, front) {
        glView?.setParams(params, if (comparing) 0f else strength)
        glView?.setMirror(front)
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1500)
            toast = null
        }
    }

    // 앱이 백그라운드로 갈 때 GL 렌더링도 같이 멈추고 재개
    DisposableEffect(glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView?.onResume()
                Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                else -> {}
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // GL 준비 완료 / 전·후면 전환 시 카메라 연결
    DisposableEffect(glView, glToken, front) {
        val view = glView
        if (view == null || glToken == 0) {
            return@DisposableEffect onDispose { }
        }
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1440, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()
                val preview = Preview.Builder().setResolutionSelector(resolution).build()
                preview.setSurfaceProvider(executor, view.surfaceProvider)
                val cameraSelector =
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(activity, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("FilmCam", "카메라 연결 실패", e)
            }
        }, executor)
        onDispose { provider?.unbindAll() }
    }

    fun shoot() {
        val v = glView ?: return
        v.capture { bmp ->
            Thread {
                val ok = saveBitmap(context, bmp)
                activity.runOnUiThread { toast = if (ok) "저장했어요 ✓" else "저장에 실패했어요" }
            }.start()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ── 미리보기 (길게 누르면 원본과 비교)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clipToBounds(),
        ) {
            AndroidView(
                factory = { ctx ->
                    CameraGLView(ctx).also { v ->
                        v.onReady = { glToken++ }
                        glView = v
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                comparing = true
                                tryAwaitRelease()
                                comparing = false
                            },
                        )
                    },
            )
            if (comparing) {
                Text(
                    "원본",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(Color(0x88000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            toast?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // ── 탭: 필터 / 조절
        Row(Modifier.fillMaxWidth()) {
            listOf("필터", "조절").forEachIndexed { i, label ->
                Text(
                    label,
                    color = if (tab == i) Color.White else Color(0xFF9A9A9A),
                    fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                FilterTab(
                    selected = selected,
                    onSelect = { selected = it },
                    strength = strength,
                    onStrength = { strength = it },
                )
            } else {
                AdjustTab(adjust)
            }
        }

        // ── 촬영 줄
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(preset.name, color = Color(0xFF9A9A9A), fontSize = 13.sp)
            }
            ShutterButton(onClick = { shoot() })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "↻ 전환",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { front = !front }
                        .padding(12.dp),
                )
            }
        }
    }
}

/** 갤러리(Pictures/FilmCam)에 JPEG 로 저장. Android 10+ 는 별도 저장소 권한이 필요 없어요. */
fun saveBitmap(context: Context, bmp: Bitmap): Boolean {
    return try {
        val name = "FilmCam_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FilmCam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val written = resolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        } ?: false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        written
    } catch (e: Exception) {
        Log.e("FilmCam", "저장 실패", e)
        false
    }
}
