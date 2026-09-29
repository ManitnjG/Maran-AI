package ai.maran.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MaranColors = darkColorScheme(
    primary=Color(0xFFA8F0CE), onPrimary=Color(0xFF08251B),
    primaryContainer=Color(0xFF203E34), onPrimaryContainer=Color(0xFFC4F8E0),
    secondary=Color(0xFFB9B1F5), onSecondary=Color(0xFF211A42),
    background=Color(0xFF0B0F14), onBackground=Color(0xFFF3F5F7),
    surface=Color(0xFF11171E), onSurface=Color(0xFFF3F5F7),
    surfaceVariant=Color(0xFF1B242E), onSurfaceVariant=Color(0xFFB0BBC9),
    outline=Color(0xFF687685), outlineVariant=Color(0xFF2B3642),
    error=Color(0xFFFFB4AB)
)
@Composable fun MaranTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=MaranColors, shapes=Shapes(
        small=RoundedCornerShape(12.dp), medium=RoundedCornerShape(20.dp), large=RoundedCornerShape(24.dp)
    ), content=content)
}
@Composable fun PremiumCard(modifier:Modifier=Modifier, content:@Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(), border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp), content=content)
    }
}
@Composable fun StatusLabel(text:String) {
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(50)) {
        Text(text.replace('_',' '),Modifier.padding(horizontal=10.dp,vertical=6.dp),
            color=MaterialTheme.colorScheme.onPrimaryContainer,style=MaterialTheme.typography.labelMedium)
    }
}
@Composable fun EmptyState(title:String,description:String) {
    PremiumCard { Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}
