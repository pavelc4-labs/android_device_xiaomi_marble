package com.xiaomi.parts.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

object MaterialShapes {
    val Cookie9: Shape by lazy {
        polygonShape { RoundedPolygon.star(9, innerRadius = 0.62f, rounding = CornerRounding(0.55f)) }
    }
    val Scallop12: Shape by lazy {
        polygonShape { RoundedPolygon.star(12, innerRadius = 0.84f, rounding = CornerRounding(0.6f)) }
    }

    private fun polygonShape(build: () -> RoundedPolygon): Shape =
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val polygon = build()
                val basePath: Path = polygon.toPath().asComposePath()
                val bounds = polygon.calculateBounds()
                val srcW = (bounds[2] - bounds[0]).coerceAtLeast(0.0001f)
                val srcH = (bounds[3] - bounds[1]).coerceAtLeast(0.0001f)
                val sx = size.width / srcW
                val sy = size.height / srcH
                val out = Path()
                val xform = androidx.compose.ui.graphics.Matrix().apply {
                    scale(sx, sy)
                    translate(-bounds[0], -bounds[1])
                }
                out.addPath(basePath)
                out.transform(xform)
                return Outline.Generic(out)
            }
        }
}
