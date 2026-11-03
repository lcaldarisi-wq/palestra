package it.palestra.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import it.palestra.app.AppContainer
import it.palestra.app.PalestraApp
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.theme.LabelCaps
import it.palestra.app.ui.theme.Pal

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as PalestraApp).container

/** ViewModel con accesso al contenitore delle dipendenze, legato alla schermata corrente. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val c = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(c) } })
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onBack)
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, color = Pal.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Pal.Text, container: Color = Pal.Secondary) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(container).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = LabelCaps, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Pal.Muted, fontSize = 12.sp)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = Pal.Secondary,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, subColor: Color = Pal.Muted) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Pal.SurfaceLow)
            .border(1.dp, Pal.Secondary, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Pal.Muted, fontSize = 12.sp, maxLines = 1)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, color = subColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Pal.Accent, contentColor = Pal.OnAccent, disabledContainerColor = Pal.Secondary, disabledContentColor = Pal.Muted),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Pal.Accent) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Pill(text: String, filled: Boolean = true, color: Color = Pal.Accent) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = if (filled) Pal.OnAccent else color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color, RoundedCornerShape(50)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Pal.Accent) else Modifier.border(1.dp, Pal.OutlineSoft, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Pal.OnAccent else Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.Secondary).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (i == selected) Pal.Accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (i == selected) Pal.OnAccent else Pal.TextSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Campo numerico compatto: mantiene il testo digitato e salva solo valori validi. */
@Composable
fun NumberBox(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    fun show(v: Double) = if (v == 0.0) "" else if (decimal) Fmt.num(v) else v.toLong().toString()
    var text by remember { mutableStateOf(show(value)) }
    LaunchedEffect(value) {
        val parsed = if (decimal) Fmt.parseDecimal(text) else Fmt.parseInt(text)?.toDouble()
        if (parsed != value) text = show(value)
    }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = text,
        onValueChange = { new ->
            val filtered = new.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(7)
            text = filtered
            val parsed = if (decimal) Fmt.parseDecimal(filtered) else Fmt.parseInt(filtered)?.toDouble()
            if (parsed != null) onValue(parsed)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = Pal.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
        cursorBrush = SolidColor(Pal.Accent),
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Pal.SurfaceLow)
            .border(1.dp, if (highlight) Pal.Accent else Pal.OutlineSoft, shape),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text("–", color = Pal.Dim, fontSize = 17.sp)
                inner()
            }
        },
    )
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Pal.Muted, fontSize = 12.sp)
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.SurfaceLow).border(1.dp, Pal.OutlineSoft, RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, "Diminuisci $label", tint = Pal.TextSoft, modifier = Modifier.size(18.dp))
            }
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 40.dp))
            Box(Modifier.size(40.dp).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "Aumenta $label", tint = Pal.TextSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Pal.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Pal.OnAccent,
                checkedTrackColor = Pal.Accent,
                uncheckedThumbColor = Pal.Muted,
                uncheckedTrackColor = Pal.SurfaceLow,
                uncheckedBorderColor = Pal.Outline,
            ),
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Pal.Secondary), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Pal.Accent, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, color = Pal.Muted, textAlign = TextAlign.Center, fontSize = 14.sp)
        if (action != null) {
            Spacer(Modifier.height(4.dp)); action()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Secondary,
        title = { Text(title) },
        text = { Text(text, color = Pal.TextSoft) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) Pal.Failed else Pal.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = Pal.TextSoft) } },
    )
}

@Composable
fun AppTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, color = Pal.Dim) }
        } else {
            null
        },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.Accent,
            unfocusedBorderColor = Pal.OutlineSoft,
            focusedLabelColor = Pal.Accent,
            cursorColor = Pal.Accent,
            focusedContainerColor = Pal.SurfaceLow,
            unfocusedContainerColor = Pal.SurfaceLow,
        ),
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(shape).background(Pal.Secondary).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = Pal.Muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Pal.Dim, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Pal.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Pal.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Cancella ricerca", tint = Pal.Muted, modifier = Modifier.size(20.dp).clickable { onValueChange("") })
        }
    }
}
