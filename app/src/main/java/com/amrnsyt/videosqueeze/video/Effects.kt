@file:OptIn(UnstableApi::class)

package com.amrnsyt.videosqueeze.video

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.util.concurrent.Executor

/**
 * One GPU effect that can do two things:
 *  - Enhance: unsharp-mask sharpening plus an HDR-style tone and colour look.
 *  - Smooth: inserts a blended frame between every pair of frames (2x frame rate).
 */
class ProcessEffect(
    private val blend: Boolean,
    private val strength: Float
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ProcessProgram(useHdr, blend, strength)
}

private class ProcessProgram(
    private val useHdr: Boolean,
    private val blend: Boolean,
    strength: Float
) : GlShaderProgram {

    private val sharp = 0.8f * strength
    private val look = strength

    private var inputListener: GlShaderProgram.InputListener? = null
    private var outputListener: GlShaderProgram.OutputListener? = null
    private var errorListener: GlShaderProgram.ErrorListener? = null
    private var errorExecutor: Executor? = null

    private var program: GlProgram? = null
    private val pool = ArrayList<GlTextureInfo>()
    private val free = ArrayDeque<GlTextureInfo>()
    private var prevTex: GlTextureInfo? = null
    private var havePrev = false
    private var prevPts = 0L

    override fun setInputListener(inputListener: GlShaderProgram.InputListener) {
        this.inputListener = inputListener
        inputListener.onReadyToAcceptInputFrame()
    }

    override fun setOutputListener(outputListener: GlShaderProgram.OutputListener) {
        this.outputListener = outputListener
    }

    override fun setErrorListener(errorListenerExecutor: Executor, errorListener: GlShaderProgram.ErrorListener) {
        this.errorExecutor = errorListenerExecutor
        this.errorListener = errorListener
    }

    override fun queueInputFrame(
        glObjectsProvider: GlObjectsProvider,
        inputTexture: GlTextureInfo,
        presentationTimeUs: Long
    ) {
        try {
            ensureResources(glObjectsProvider, inputTexture.width, inputTexture.height)
            val prev = prevTex

            if (blend && havePrev && prev != null) {
                val mid = free.removeFirst()
                draw(mid, prev.texId, inputTexture.texId, 0.5f, enhance = true)
                outputListener?.onOutputFrameAvailable(mid, (prevPts + presentationTimeUs) / 2)
            }

            val out = free.removeFirst()
            draw(out, inputTexture.texId, inputTexture.texId, 1f, enhance = true)
            outputListener?.onOutputFrameAvailable(out, presentationTimeUs)

            if (blend && prev != null) {
                draw(prev, inputTexture.texId, inputTexture.texId, 1f, enhance = false)
                havePrev = true
                prevPts = presentationTimeUs
            }

            inputListener?.onInputFrameProcessed(inputTexture)
            if (free.size >= 2) inputListener?.onReadyToAcceptInputFrame() else awaitingReady = true
        } catch (e: Exception) {
            raise(e)
        }
    }

    private var awaitingReady = false

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        free.addLast(outputTexture)
        if (awaitingReady && free.size >= 2) {
            awaitingReady = false
            inputListener?.onReadyToAcceptInputFrame()
        }
    }

    override fun signalEndOfCurrentInputStream() {
        outputListener?.onCurrentOutputStreamEnded()
    }

    override fun flush() {
        free.clear()
        free.addAll(pool)
        havePrev = false
        awaitingReady = false
        inputListener?.onFlush()
        if (free.size >= 2) inputListener?.onReadyToAcceptInputFrame() else awaitingReady = true
    }

    override fun release() {
        try {
            program?.delete()
            program = null
            for (t in pool) {
                GlUtil.deleteTexture(t.texId)
                GlUtil.deleteFbo(t.fboId)
            }
            prevTex?.let {
                GlUtil.deleteTexture(it.texId)
                GlUtil.deleteFbo(it.fboId)
            }
            pool.clear()
            free.clear()
            prevTex = null
        } catch (e: Exception) {
            throw VideoFrameProcessingException.from(e)
        }
    }

    // ---------- GL ----------

    private fun ensureResources(provider: GlObjectsProvider, w: Int, h: Int) {
        if (program != null) return
        val p = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        p.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        p.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
        p.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix())
        program = p

        repeat(POOL_SIZE) {
            val tex = newTexture(provider, w, h)
            pool.add(tex)
            free.addLast(tex)
        }
        if (blend) prevTex = newTexture(provider, w, h)
    }

    private fun newTexture(provider: GlObjectsProvider, w: Int, h: Int): GlTextureInfo {
        val texId = GlUtil.createTexture(w, h, useHdr)
        return provider.createBuffersForTexture(texId, w, h)
    }

    private fun draw(target: GlTextureInfo, texA: Int, texB: Int, mix: Float, enhance: Boolean) {
        val p = program ?: return
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        GlUtil.clearFocusedBuffers()
        p.use()
        p.setSamplerTexIdUniform("uTexA", texA, 0)
        p.setSamplerTexIdUniform("uTexB", texB, 1)
        p.setFloatUniform("uMix", mix)
        p.setFloatUniform("uSharp", if (enhance) sharp else 0f)
        p.setFloatUniform("uLook", if (enhance) look else 0f)
        p.setFloatsUniform("uTexel", floatArrayOf(1f / target.width, 1f / target.height))
        p.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    private fun raise(e: Exception) {
        val ex = VideoFrameProcessingException.from(e)
        val listener = errorListener
        val executor = errorExecutor
        if (listener != null && executor != null) executor.execute { listener.onError(ex) } else throw ex
    }

    private companion object {
        const val POOL_SIZE = 4

        const val VERTEX_SHADER = """#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        const val FRAGMENT_SHADER = """#version 100
precision highp float;
uniform sampler2D uTexA;
uniform sampler2D uTexB;
uniform float uMix;
uniform vec2 uTexel;
uniform float uSharp;
uniform float uLook;
varying vec2 vTexSamplingCoord;

vec3 fetch(sampler2D t, vec2 uv) {
  vec3 c = texture2D(t, uv).rgb;
  if (uSharp > 0.0) {
    vec3 b = (texture2D(t, uv + vec2(uTexel.x, 0.0)).rgb
            + texture2D(t, uv - vec2(uTexel.x, 0.0)).rgb
            + texture2D(t, uv + vec2(0.0, uTexel.y)).rgb
            + texture2D(t, uv - vec2(0.0, uTexel.y)).rgb) * 0.25;
    c = c + (c - b) * uSharp;
  }
  return c;
}

// HDR-style tone: lift shadows, roll off highlights, S-curve, vibrance.
vec3 hdrLook(vec3 c, float k) {
  float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
  float shadows = (1.0 - smoothstep(0.0, 0.5, l)) * 0.06 * k;
  float highlights = smoothstep(0.7, 1.0, l);
  c = c + shadows;
  c = mix(c, c * (1.0 - 0.12 * k), highlights);
  c = clamp(c, 0.0, 1.0);
  c = mix(c, c * c * (3.0 - 2.0 * c), 0.35 * k);
  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float boost = 1.0 + (1.0 - (mx - mn)) * 0.35 * k;
  float l2 = dot(c, vec3(0.2126, 0.7152, 0.0722));
  return mix(vec3(l2), c, boost);
}

void main() {
  vec3 c;
  if (uMix >= 1.0) {
    c = fetch(uTexB, vTexSamplingCoord);
  } else {
    c = mix(fetch(uTexA, vTexSamplingCoord), fetch(uTexB, vTexSamplingCoord), uMix);
  }
  if (uLook > 0.0) {
    c = hdrLook(c, uLook);
  }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
