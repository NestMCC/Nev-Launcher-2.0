package com.movtery.zalithlauncher.ui.screens.main.control_editor.edit_style

import androidx.compose.ui.graphics.Color
import com.movtery.layer_controller.data.ButtonShape
import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.createNewButtonStyle

/**
 * Gaya tombol siap pakai. Setiap kali [create] dipanggil akan dibuat gaya baru (UUID baru),
 * sehingga preset yang sama bisa ditambahkan berkali-kali tanpa bentrok.
 */
class ButtonStylePreset(
    val name: String,
    private val config: ButtonStyle.StyleConfig
) {
    fun create(): ButtonStyle = createNewButtonStyle(name).copy(
        animateSwap = true,
        commonStyle = true,
        lightStyle = config,
        darkStyle = config
    )
}

private fun styleConfig(
    background: Color,
    pressedBackground: Color,
    content: Color,
    border: Color,
    borderWidth: Int,
    radius: ButtonShape,
    pressedBorder: Color = border,
    pressedContent: Color = content
) = ButtonStyle.StyleConfig(
    alpha = 1f,
    pressedAlpha = 1f,
    backgroundColor = background,
    pressedBackgroundColor = pressedBackground,
    contentColor = content,
    pressedContentColor = pressedContent,
    fontSize = null,
    pressedFontSize = null,
    borderWidth = borderWidth,
    pressedBorderWidth = borderWidth,
    borderColor = border,
    pressedBorderColor = pressedBorder,
    borderRadius = radius,
    pressedBorderRadius = radius
)

private val Red = Color(0xFFE53935)
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF00E676)
private val Gold = Color(0xFFFFC107)

val ButtonStylePresets: List<ButtonStylePreset> = listOf(
    //Tema utama launcher: merah gelap bergaya gaming
    ButtonStylePreset(
        name = "Merah Gaming",
        config = styleConfig(
            background = Color(0xFF1A0A0A).copy(alpha = 0.7f),
            pressedBackground = Red.copy(alpha = 0.85f),
            content = Color.White,
            border = Red,
            borderWidth = 2,
            radius = ButtonShape(12f)
        )
    ),
    //Transparan seperti kaca
    ButtonStylePreset(
        name = "Kaca",
        config = styleConfig(
            background = Color.White.copy(alpha = 0.16f),
            pressedBackground = Color.White.copy(alpha = 0.38f),
            content = Color.White,
            border = Color.White.copy(alpha = 0.45f),
            borderWidth = 1,
            radius = ButtonShape(16f)
        )
    ),
    //Garis tepi neon
    ButtonStylePreset(
        name = "Neon Biru",
        config = styleConfig(
            background = Color.Black.copy(alpha = 0.35f),
            pressedBackground = Cyan.copy(alpha = 0.4f),
            content = Cyan,
            border = Cyan,
            borderWidth = 2,
            radius = ButtonShape(8f),
            pressedContent = Color.White
        )
    ),
    ButtonStylePreset(
        name = "Neon Hijau",
        config = styleConfig(
            background = Color.Black.copy(alpha = 0.35f),
            pressedBackground = Green.copy(alpha = 0.4f),
            content = Green,
            border = Green,
            borderWidth = 2,
            radius = ButtonShape(8f),
            pressedContent = Color.White
        )
    ),
    //Hanya garis tepi, tanpa isi
    ButtonStylePreset(
        name = "Garis Minimal",
        config = styleConfig(
            background = Color.Transparent,
            pressedBackground = Color.White.copy(alpha = 0.25f),
            content = Color.White,
            border = Color.White,
            borderWidth = 2,
            radius = ButtonShape(0f)
        )
    ),
    //Bentuk bulat/kapsul
    ButtonStylePreset(
        name = "Kapsul",
        config = styleConfig(
            background = Color(0xFF263238).copy(alpha = 0.8f),
            pressedBackground = Color(0xFF546E7A).copy(alpha = 0.9f),
            content = Color.White,
            border = Color.Transparent,
            borderWidth = 0,
            radius = ButtonShape(50f)
        )
    ),
    //Sudut miring bergaya futuristik
    ButtonStylePreset(
        name = "Sudut Miring",
        config = styleConfig(
            background = Color(0xFF212121).copy(alpha = 0.75f),
            pressedBackground = Red.copy(alpha = 0.8f),
            content = Color.White,
            border = Red,
            borderWidth = 2,
            radius = ButtonShape(4f, 20f, 4f, 20f)
        )
    ),
    //Emas
    ButtonStylePreset(
        name = "Emas",
        config = styleConfig(
            background = Color(0xFF2B2100).copy(alpha = 0.75f),
            pressedBackground = Gold.copy(alpha = 0.6f),
            content = Gold,
            border = Gold,
            borderWidth = 2,
            radius = ButtonShape(10f),
            pressedContent = Color.Black
        )
    ),
    //Solid gelap sederhana
    ButtonStylePreset(
        name = "Gelap Solid",
        config = styleConfig(
            background = Color.Black.copy(alpha = 0.8f),
            pressedBackground = Color.DarkGray,
            content = Color.White,
            border = Color.Transparent,
            borderWidth = 0,
            radius = ButtonShape(6f)
        )
    )
)
