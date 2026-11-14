package com.filmcam.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 카메라가 찍은 원본 해상도 JPEG 에 미리보기와 같은 필터를 입혀서 돌려준다.
 * 화면 크기로 저장하던 것보다 훨씬 높은 해상도로 저장돼요.
 */
object PhotoProcessor {

    private const val MAX_SIDE = 4096

    /**
     * @param jpeg 카메라가 찍은 JPEG 바이트
     * @param rotation 사진을 바로 세우려면 시계방향으로 돌려야 하는 각도 (0/90/180/270)
     * @param flipX true 면 좌우를 뒤집는다 (셀카 거울 모드)
     */
    fun process(
        jpeg: ByteArray,
        rotation: Int,
        params: FilterParams,
        intensity: Float,
        flipX: Boolean,
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
            ?: throw IllegalStateException("사진을 읽지 못했어요")

        val rot = ((rotation % 360) + 360) % 360
        val swap = rot == 90 || rot == 270
        val outW = if (swap) src.height else src.width
        val outH = if (swap) src.width else src.height

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)

        val configAttrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: throw IllegalStateException("EGL 설정을 찾지 못했어요")

        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        val surface = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, outW, EGL14.EGL_HEIGHT, outH, EGL14.EGL_NONE), 0,
        )

        try {
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throw IllegalStateException("EGL 를 켜지 못했어요")
            }

            val program = buildProgram(Shaders.VERTEX, Shaders.FRAGMENT_2D)
            GLES20.glUseProgram(program)
            GLES20.glViewport(0, 0, outW, outH)

            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, src, 0)
            src.recycle() // 메모리를 먼저 돌려준다

            fun loc(name: String) = GLES20.glGetUniformLocation(program, name)

            GLES20.glUniform1i(loc("uTex"), 0)
            GLES20.glUniformMatrix4fv(loc("uTexMatrix"), 1, false, texMatrix(rot, flipX), 0)
            if (swap) {
                GLES20.glUniform2f(loc("uTexel"), 1f / 1440f, 1f / 1080f)
            } else {
                GLES20.glUniform2f(loc("uTexel"), 1f / 1080f, 1f / 1440f)
            }
            GLES20.glUniform1f(loc("uExposure"), params.exposure)
            GLES20.glUniform1f(loc("uContrast"), params.contrast)
            GLES20.glUniform1f(loc("uSaturation"), params.saturation)
            GLES20.glUniform1f(loc("uTemp"), params.temperature)
            GLES20.glUniform1f(loc("uTint"), params.tint)
            GLES20.glUniform1f(loc("uFade"), params.fade)
            GLES20.glUniform1f(loc("uGlow"), params.glow)
            GLES20.glUniform1f(loc("uGrain"), params.grain)
            GLES20.glUniform1f(loc("uVignette"), params.vignette)
            GLES20.glUniform1f(loc("uMono"), params.mono)
            GLES20.glUniform1f(loc("uSplit"), params.split)
            GLES20.glUniform1f(loc("uIntensity"), intensity)
            GLES20.glUniform1f(loc("uTime"), 0.37f)
            // 해상도가 높아도 그레인 알갱이가 미리보기와 비슷한 굵기로 보이게
            GLES20.glUniform1f(loc("uPixelScale"), maxOf(1f, minOf(outW, outH) / 1080f))
            GLES20.glUniform3f(
                loc("uShadowTint"),
                params.shadowTint.r, params.shadowTint.g, params.shadowTint.b,
            )
            GLES20.glUniform3f(
                loc("uHighlightTint"),
                params.highlightTint.r, params.highlightTint.g, params.highlightTint.b,
            )

            val quad = ByteBuffer.allocateDirect(8 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                    position(0)
                }
            val posLoc = GLES20.glGetAttribLocation(program, "aPos")
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(posLoc)

            // 아래 줄부터 읽히는데, 행렬에서 이미 위아래를 맞춰 두었기 때문에 그대로 비트맵이 된다
            val buf = ByteBuffer.allocateDirect(outW * outH * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            out.copyPixelsFromBuffer(buf)

            GLES20.glDeleteTextures(1, tex, 0)
            GLES20.glDeleteProgram(program)
            return out
        } finally {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }

    /**
     * 출력 좌표(x: 왼→오, y: 읽는 순서 기준 위→아래)에서 사진 텍스처 좌표(s, t)를 만드는 행렬.
     * s = a*x + b*y + c, t = d*x + e*y + f
     */
    private fun texMatrix(rotation: Int, flipX: Boolean): FloatArray {
        val v = when (rotation) {
            90 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 1f)
            180 -> floatArrayOf(-1f, 0f, 1f, 0f, -1f, 1f)
            270 -> floatArrayOf(0f, -1f, 1f, 1f, 0f, 0f)
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        }
        var a = v[0]
        val b = v[1]
        var c = v[2]
        var d = v[3]
        val e = v[4]
        var f = v[5]
        if (flipX) {
            c += a
            a = -a
            f += d
            d = -d
        }
        val m = FloatArray(16)
        m[0] = a
        m[1] = d
        m[4] = b
        m[5] = e
        m[10] = 1f
        m[12] = c
        m[13] = f
        m[15] = 1f
        return m
    }

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
