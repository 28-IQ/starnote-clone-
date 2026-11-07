package clone.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto is the Android system default and is open source. Generated from tokens.json.
val NoteType = Typography(
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(500)),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(400)),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(400)),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight(600)),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight(700)),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight(700)),
)
