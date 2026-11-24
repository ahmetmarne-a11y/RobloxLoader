package com.reaper.engine

import android.content.Context
import android.graphics.*
import android.view.View

class ESPCanvas(context: Context) : View(context) {

    var enabled = false

    data class ESPPlayer(
        val name: String,
        val x: Float, val y: Float,
        val w: Float, val h: Float,
        val dist: Int, val hp: Int,
        val joints: List<PointF>
    )

    @Volatile var players: List<ESPPlayer> = listOf()
    @Volatile var robloxVP: PointF = PointF(1920f, 1080f)

    private val boxPaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val tracerPaint = Paint().apply {
        color = Color.GREEN; strokeWidth = 2f; isAntiAlias = true
    }
    private val skeletonPaint = Paint().apply {
        color = Color.rgb(255, 80, 80); strokeWidth = 1.8f; isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 30f
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val hpBgPaint = Paint().apply {
        color = Color.rgb(20, 20, 20); style = Paint.Style.FILL
    }
    private val hpBarPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled) { postInvalidateDelayed(16); return }

        val sx = width / robloxVP.x
        val sy = height / robloxVP.y
        val cx = width / 2f

        for (p in players) {
            if (p.dist > 280) continue

            val px   = p.x * sx
            val py   = p.y * sy
            val pw   = p.w * sx
            val ph   = p.h * sy
            val left = px - pw / 2f
            val right= px + pw / 2f
            val top  = py
            val bot  = py + ph

            // box
            canvas.drawRect(left, top, right, bot, boxPaint)

            // isim + mesafe
            canvas.drawText("${p.name} [${p.dist}m]", left, top - 6f, textPaint)

            // tracer — ekran üst ortasından ayağa (yeşil)
            canvas.drawLine(cx, 0f, px, bot, tracerPaint)

            // hp bar
            val bx = left - 7f
            val hpRatio = p.hp / 100f
            canvas.drawRect(bx, top, bx + 4f, bot, hpBgPaint)
            hpBarPaint.color = Color.rgb(
                (255 * (1f - hpRatio)).toInt(),
                (255 * hpRatio).toInt(), 0
            )
            canvas.drawRect(bx, top + ph * (1f - hpRatio), bx + 4f, bot, hpBarPaint)

            // skeleton
            val j = p.joints
            if (j.size >= 12) {
                val sj = j.map { PointF(it.x * sx, it.y * sy) }
                bone(canvas, sj[0], sj[1])   // head → torso
                bone(canvas, sj[1], sj[2])   // → lUpperArm
                bone(canvas, sj[1], sj[3])   // → rUpperArm
                bone(canvas, sj[2], sj[4])   // lUpperArm → lLowerArm
                bone(canvas, sj[3], sj[5])   // rUpperArm → rLowerArm
                bone(canvas, sj[1], sj[6])   // torso → lUpperLeg
                bone(canvas, sj[1], sj[7])   // torso → rUpperLeg
                bone(canvas, sj[6], sj[8])   // → lLowerLeg
                bone(canvas, sj[7], sj[9])   // → rLowerLeg
                bone(canvas, sj[8], sj[10])  // → lFoot
                bone(canvas, sj[9], sj[11])  // → rFoot
            }
        }
        postInvalidateDelayed(16)
    }

    private fun bone(canvas: Canvas, a: PointF, b: PointF) {
        if (a.x == 0f && a.y == 0f) return
        if (b.x == 0f && b.y == 0f) return
        canvas.drawLine(a.x, a.y, b.x, b.y, skeletonPaint)
    }
}
