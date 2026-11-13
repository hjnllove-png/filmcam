package com.filmcam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 카메라 프레임을 GPU 셰이더로 보정해서 그려주는 뷰.
 * 화면에 보이는 것과 저장되는 사진이 같은 파이프라인을 쓰기 때문에 보이는 그대로 저장돼요.
 */
class CameraGLView(context: Context) : GLSurfaceView(context) {

    /** GL 준비(또는 재생성)가 끝나면 메인 스레드에서 호출됨 → 이때 카메라를 연결 */
    var onReady: (() -> Unit)? = null

    private val renderer = FilterRenderer()

    val surfaceProvider: Preview.SurfaceProvider =
        Preview.SurfaceProvider { request ->
            renderer.provide(request, ContextCompat.getMainExecutor(context))
        }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    fun setParams(params: FilterParams, intensity: Float) {
        renderer.params = params
        renderer.intensity = intensity
        requestRender()
    }

    fun setMirror(mirror: Boolean) {
        renderer.mirror = mirror
        requestRender()
    }

    /** 다음 프레임을 필터가 적용된 비트맵으로 받아온다 (GL 스레드에서 콜백됨) */
    fun capture(callback: (Bitmap) -> Unit) {
        renderer.captureCallback = callback
        requestRender()
    }

    private inner class FilterRenderer : GLSurfaceView.Renderer {

        @Volatile var params = FilterParams()
        @Volatile var intensity = 1f
        @Volatile var mirror = false
        @Volatile var rotation = 90
        @Volatile var captureCallback: ((Bitmap) -> Unit)? = null
        @Volatile private var surfaceTexture: SurfaceTexture? = null
        @Volatile private var newFrame = false
        @Volatile private var texelX = 1f / 1440f
        @Volatile private var texelY = 1f / 1080f

        private var program = 0
        private var oesTexture = 0
        private var viewW = 1
        private var viewH = 1
        private var frameCount = 0
        private val stMatrix = FloatArray(16)
        private val uniforms = HashMap<String, Int>()

        private val quad = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = buildProgram(Shaders.VERTEX, Shaders.FRAGMENT)
            uniforms.clear()

            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            oesTexture = tex[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture?.release()
            val st = SurfaceTexture(oesTexture)
            st.setOnFrameAvailableListener {
                newFrame = true
                requestRender()
            }
            surfaceTexture = st
            post { onReady?.invoke() }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewW = width
            viewH = height
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            val st = surfaceTexture ?: return
            if (newFrame) {
                newFrame = false
                try {
                    st.updateTexImage()
                } catch (e: Exception) {
                    return
                }
            }
            st.getTransformMatrix(stMatrix)

            GLES20.glViewport(0, 0, viewW, viewH)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
            GLES20.glUniform1i(u("uTex"), 0)
            GLES20.glUniformMatrix4fv(u("uTexMatrix"), 1, false, buildTexMatrix(), 0)
            GLES20.glUniform2f(u("uTexel"), texelX, texelY)

            val p = params
            GLES20.glUniform1f(u("uExposure"), p.exposure)
            GLES20.glUniform1f(u("uContrast"), p.contrast)
            GLES20.glUniform1f(u("uSaturation"), p.saturation)
            GLES20.glUniform1f(u("uTemp"), p.temperature)
            GLES20.glUniform1f(u("uTint"), p.tint)
            GLES20.glUniform1f(u("uFade"), p.fade)
            GLES20.glUniform1f(u("uGlow"), p.glow)
            GLES20.glUniform1f(u("uGrain"), p.grain)
            GLES20.glUniform1f(u("uVignette"), p.vignette)
            GLES20.glUniform1f(u("uMono"), p.mono)
            GLES20.glUniform1f(u("uSplit"), p.split)
            GLES20.glUniform1f(u("uIntensity"), intensity)
            GLES20.glUniform3f(u("uShadowTint"), p.shadowTint.r, p.shadowTint.g, p.shadowTint.b)
            GLES20.glUniform3f(u("uHighlightTint"), p.highlightTint.r, p.highlightTint.g, p.highlightTint.b)
            frameCount = (frameCount + 1) % 1000
            GLES20.glUniform1f(u("uTime"), frameCount * 0.013f)

            val posLoc = GLES20.glGetAttribLocation(program, "aPos")
            GLES20.glEnableVertexAttribArray(posLoc)
            quad.position(0)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(posLoc)

            val cb = captureCallback
            if (cb != null) {
                captureCallback = null
                cb(readBitmap())
            }
        }

        /** CameraX 에서 Surface 요청이 올 때 호출 (메인 스레드) */
        fun provide(request: SurfaceRequest, executor: Executor) {
            val st = surfaceTexture
            if (st == null) {
                request.willNotProvideSurface()
                return
            }
            val size = request.resolution
            st.setDefaultBufferSize(size.width, size.height)
            texelX = 1f / size.width
            texelY = 1f / size.height
            val surface = Surface(st)
            request.setTransformationInfoListener(executor) { info ->
                rotation = info.rotationDegrees
                requestRender()
            }
            request.provideSurface(surface, executor) { surface.release() }
        }

        /** 센서 방향 → 화면 방향 회전(+ 셀카 좌우반전)을 SurfaceTexture 행렬과 합친다 */
        private fun buildTexMatrix(): FloatArray {
            val r = when (((rotation % 360) + 360) % 360) {
                90 -> floatArrayOf(0f, -1f, 1f, 1f, 0f, 0f)
                180 -> floatArrayOf(-1f, 0f, 1f, 0f, -1f, 1f)
                270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 1f)
                else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
            }
            var a = r[0]
            val b = r[1]
            var c = r[2]
            var d = r[3]
            val e = r[4]
            var f = r[5]
            if (mirror) {
                c += a
                a = -a
                f += d
                d = -d
            }
            val rot = FloatArray(16)
            Matrix.setIdentityM(rot, 0)
            rot[0] = a
            rot[1] = d
            rot[4] = b
            rot[5] = e
            rot[12] = c
            rot[13] = f
            val out = FloatArray(16)
            Matrix.multiplyMM(out, 0, stMatrix, 0, rot, 0)
            return out
        }

        private fun readBitmap(): Bitmap {
            val w = viewW
            val h = viewH
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buf)
            // OpenGL 은 아래쪽이 원점이라 위아래를 뒤집어준다
            val flip = android.graphics.Matrix().apply { postScale(1f, -1f) }
            return Bitmap.createBitmap(raw, 0, 0, w, h, flip, false)
        }

        private fun u(name: String): Int =
            uniforms.getOrPut(name) { GLES20.glGetUniformLocation(program, name) }

        private fun buildProgram(vs: String, fs: String): Int {
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, v)
            GLES20.glAttachShader(prog, f)
            GLES20.glLinkProgram(prog)
            val status = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                throw RuntimeException("셰이더 링크 실패: " + GLES20.glGetProgramInfoLog(prog))
            }
            return prog
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val status = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                throw RuntimeException("셰이더 컴파일 실패: " + GLES20.glGetShaderInfoLog(s))
            }
            return s
        }
    }
}
