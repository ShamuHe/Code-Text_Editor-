
package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import java.io.File
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import kotlinx.coroutines.*
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding

// Language extensions mapping
val languageExtensions = mapOf(
    "Plain Text" to "txt",
    "Kotlin" to "kt",
    "Java" to "java",
    "Python" to "py",
    "C" to "c",
    "C++" to "cpp"
)

// ADB Compilation Constants
const val PHONE_CODE_DIR = "/storage/emulated/0/Android/data/com.example.myapplication/files/code/"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TextEditorApp()
            }
        }
    }
}

// XML-based Syntax Highlighter
object SyntaxHighlighter {
    private val languageKeywords = mutableMapOf<String, Set<String>>()

    /**
     * Load keywords from XML resource file
     */
    fun loadKeywordsFromXML(context: Context, resourceId: Int) {
        try {
            val parser = context.resources.getXml(resourceId)
            var currentLanguage: String? = null
            val currentKeywords = mutableSetOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "language" -> {
                                // Save previous language keywords if any
                                currentLanguage?.let { lang ->
                                    languageKeywords[lang] = currentKeywords.toSet()
                                    currentKeywords.clear()
                                }
                                // Start new language
                                currentLanguage = parser.getAttributeValue(null, "name")
                            }
                            "keyword" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    currentKeywords.add(parser.text.trim())
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "language") {
                            // Save current language keywords
                            currentLanguage?.let { lang ->
                                languageKeywords[lang] = currentKeywords.toSet()
                                currentKeywords.clear()
                            }
                            currentLanguage = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            Log.e("SyntaxHighlighter", "XML parsing error", e)
        } catch (e: IOException) {
            Log.e("SyntaxHighlighter", "IO error", e)
        }
    }

    /**
     * Get keywords for specific language
     */
    fun getKeywordsForLanguage(language: String): Set<String> {
        return languageKeywords[language.lowercase()] ?: emptySet()
    }

    /**
     * Get all loaded languages
     */
    fun getLoadedLanguages(): Set<String> {
        return languageKeywords.keys
    }

    /**
     * Check if keywords are loaded
     */
    fun areKeywordsLoaded(): Boolean {
        return languageKeywords.isNotEmpty()
    }

    /**
     * Advanced syntax highlighting with comprehensive language support
     */
    fun highlightSyntax(text: String, language: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val keywords = getKeywordsForLanguage(language)

        // Professional color scheme for code editors
        val keywordColor = Color(0xFF569CD6)      // Blue for keywords
        val stringColor = Color(0xFFCE9178)       // Orange for strings
        val commentColor = Color(0xFF6A9955)      // Green for comments
        val numberColor = Color(0xFFB5CEA8)       // Light green for numbers
        val defaultColor = Color(0xFFD4D4D4)      // Light gray for default text
        val operatorColor = Color(0xFFD4D4D4)     // White for operators
        val functionColor = Color(0xFFDCDCAA)     // Yellow for functions
        val typeColor = Color(0xFF4EC9B0)         // Cyan for types
        val preprocessorColor = Color(0xFF9B9B9B) // Gray for preprocessor directives

        var i = 0
        while (i < text.length) {
            val char = text[i]

            when {
                // Handle preprocessor directives (C/C++)
                language in setOf("c", "cpp") && char == '#' -> {
                    val directiveStart = i
                    while (i < text.length && text[i] != '\n') i++
                    builder.append(AnnotatedString(
                        text.substring(directiveStart, i),
                        SpanStyle(color = preprocessorColor, fontWeight = FontWeight.Bold)
                    ))
                }

                // Single line comments (//, #)
                (language in setOf("kotlin", "java", "c", "cpp") &&
                        i < text.length - 1 && text.substring(i, i + 2) == "//") ||
                        (language == "python" && char == '#') -> {
                    val commentStart = i
                    while (i < text.length && text[i] != '\n') i++
                    builder.append(AnnotatedString(
                        text.substring(commentStart, i),
                        SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    ))
                }

                // Multi-line comments (/* */)
                language in setOf("kotlin", "java", "c", "cpp") &&
                        i < text.length - 1 && text.substring(i, i + 2) == "/*" -> {
                    val commentStart = i
                    i += 2
                    while (i < text.length - 1 && text.substring(i, i + 2) != "*/") i++
                    if (i < text.length - 1) i += 2
                    builder.append(AnnotatedString(
                        text.substring(commentStart, i),
                        SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    ))
                }

                // Python docstrings and triple-quoted strings
                language == "python" && i < text.length - 2 &&
                        (text.substring(i, i + 3) == "\"\"\"" || text.substring(i, i + 3) == "'''") -> {
                    val stringStart = i
                    val quote = text.substring(i, i + 3)
                    i += 3

                    while (i < text.length - 2) {
                        if (text.substring(i, i + 3) == quote) {
                            i += 3
                            break
                        }
                        i++
                    }
                    builder.append(AnnotatedString(
                        text.substring(stringStart, i),
                        SpanStyle(color = stringColor)
                    ))
                }

                // Regular string literals
                char == '"' || char == '\'' -> {
                    val stringStart = i
                    i++

                    while (i < text.length) {
                        if (text[i] == char) {
                            i++
                            break
                        } else if (text[i] == '\\' && i < text.length - 1) {
                            i += 2 // Skip escaped character
                        } else {
                            i++
                        }
                    }
                    builder.append(AnnotatedString(
                        text.substring(stringStart, i),
                        SpanStyle(color = stringColor)
                    ))
                }

                // Character literals (C/C++)
                language in setOf("c", "cpp") && char == '\'' -> {
                    val charStart = i
                    i++ // Skip opening quote
                    if (i < text.length && text[i] == '\\') i++ // Skip escape char
                    if (i < text.length) i++ // Skip character
                    if (i < text.length && text[i] == '\'') i++ // Skip closing quote
                    builder.append(AnnotatedString(
                        text.substring(charStart, i),
                        SpanStyle(color = stringColor)
                    ))
                }

                // Numbers (integers, floats, hex, binary, scientific notation)
                char.isDigit() || (char == '.' && i < text.length - 1 && text[i + 1].isDigit()) ||
                        (char == '0' && i < text.length - 1 && text[i + 1].lowercase() in setOf("x", "b")) -> {
                    val numberStart = i

                    // Handle hex numbers (0x...)
                    if (char == '0' && i < text.length - 1 && text[i + 1].lowercase() == "x") {
                        i += 2
                        while (i < text.length && text[i].lowercase() in "0123456789abcdef") i++
                    }
                    // Handle binary numbers (0b...)
                    else if (char == '0' && i < text.length - 1 && text[i + 1].lowercase() == "b") {
                        i += 2
                        while (i < text.length && text[i] in "01") i++
                    }
                    // Handle decimal numbers
                    else {
                        while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++

                        // Handle scientific notation (e.g., 1.5e10, 2E-5)
                        if (i < text.length && text[i].lowercase() == "e") {
                            i++
                            if (i < text.length && text[i] in "+-") i++
                            while (i < text.length && text[i].isDigit()) i++
                        }

                        // Handle number suffixes (f, l, u, etc.)
                        while (i < text.length && text[i].lowercase() in "flud") i++
                    }

                    builder.append(AnnotatedString(
                        text.substring(numberStart, i),
                        SpanStyle(color = numberColor)
                    ))
                }

                // Keywords and identifiers
                char.isLetter() || char == '_' -> {
                    val wordStart = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    val word = text.substring(wordStart, i)

                    // Skip whitespace to check for function calls
                    var j = i
                    while (j < text.length && text[j].isWhitespace()) j++

                    val color = when {
                        keywords.contains(word) -> keywordColor
                        // Function calls (word followed by parenthesis)
                        j < text.length && text[j] == '(' -> functionColor
                        // Common types (capitalized words)
                        word.isNotEmpty() && word[0].isUpperCase() && word.length > 1 -> typeColor
                        else -> defaultColor
                    }

                    val style = if (keywords.contains(word)) {
                        SpanStyle(color = color, fontWeight = FontWeight.Bold)
                    } else {
                        SpanStyle(color = color)
                    }

                    builder.append(AnnotatedString(word, style))
                }

                // Operators and punctuation
                char in "+-*/=<>!&|^%~?:;,()[]{}." -> {
                    builder.append(AnnotatedString(char.toString(), SpanStyle(color = operatorColor)))
                    i++
                }

                // Default characters (whitespace, etc.)
                else -> {
                    builder.append(AnnotatedString(char.toString(), SpanStyle(color = defaultColor)))
                    i++
                }
            }
        }

        return builder.toAnnotatedString()
    }
}

@Composable
fun TextEditorApp() {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var undoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    var redoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("Untitled") }
    var isModified by remember { mutableStateOf(false) }
    var keywordsLoaded by remember { mutableStateOf(false) }
    var showOutputDialog by remember { mutableStateOf(false) }
    var compilationOutput by remember { mutableStateOf("") }
    var compilationStatus by remember { mutableStateOf("") }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showFindDialog by remember { mutableStateOf(false) } // Added for find dialog
    var showReplaceDialog by remember { mutableStateOf(false) } // Added for replace dialog

    // Find and replace state
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var currentFindIndex by remember { mutableStateOf(-1) }
    var findResults by remember { mutableStateOf(listOf<Int>()) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // File management
    val openStorageDir = context.getExternalFilesDir(null) ?: File("/storage/emulated/0/Android/data/${context.packageName}/files/")
    val fileList = openStorageDir.listFiles()?.map { it.name } ?: emptyList()

    // Load keywords on first composition
    LaunchedEffect(Unit) {
        try {
            SyntaxHighlighter.loadKeywordsFromXML(context, R.xml.keywords_all_languages)
            keywordsLoaded = SyntaxHighlighter.areKeywordsLoaded()
            if (keywordsLoaded) {
                Log.d("TextEditor", "Keywords loaded successfully")
                Log.d("TextEditor", "Available languages: ${SyntaxHighlighter.getLoadedLanguages()}")
            } else {
                Log.w("TextEditor", "No keywords loaded from XML")
            }
        } catch (e: Exception) {
            Log.e("TextEditor", "Failed to load keywords", e)
            keywordsLoaded = false
        }
    }

    // Statistics calculations
    val wordCount = remember(textFieldValue.text) {
        if (textFieldValue.text.isBlank()) 0 else textFieldValue.text.trim().split("\\s+".toRegex()).size
    }
    val charCount = textFieldValue.text.length
    val lineCount = if (textFieldValue.text.isEmpty()) 1 else textFieldValue.text.count { it == '\n' } + 1
    val cursorPosition = textFieldValue.selection.start
    val currentLine = textFieldValue.text.substring(0, cursorPosition).count { it == '\n' } + 1
    val currentColumn = cursorPosition - textFieldValue.text.substring(0, cursorPosition).lastIndexOf('\n')

    // Language selection
    var expandedLanguage by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("Plain Text") }
    val languages = listOf("Plain Text", "Kotlin", "Java", "Python", "C", "C++")

    // Undo/Redo helpers
    fun addToUndoStack(value: TextFieldValue) {
        undoStack = (undoStack + value).takeLast(50)
        redoStack = emptyList()
        isModified = true
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val lastState = undoStack.last()
            redoStack = redoStack + textFieldValue
            textFieldValue = lastState
            undoStack = undoStack.dropLast(1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.last()
            undoStack = undoStack + textFieldValue
            textFieldValue = nextState
            redoStack = redoStack.dropLast(1)
        }
    }

    // Save function with proper error handling
    fun saveFile(fileNameInput: String) {
        try {
            if (!openStorageDir.exists()) {
                openStorageDir.mkdirs()
            }

            // Get the extension for the current language
            val extension = languageExtensions[selectedLanguage] ?: "txt"

            // Determine the final filename
            val finalFileName = if (fileNameInput.contains('.')) {
                // If user included an extension, use it as-is
                fileNameInput
            } else {
                // If no extension provided, add the appropriate one
                "$fileNameInput.$extension"
            }

            val file = File(openStorageDir, finalFileName)
            file.writeText(textFieldValue.text)

            // Update the displayed filename without extension
            fileName = finalFileName.substringBeforeLast(".")
            isModified = false

            Toast.makeText(context, "Saved as: $finalFileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TextEditor", "Error saving file", e)
            Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Open function with language detection
    fun openFile(fileNameToOpen: String) {
        try {
            val file = File(openStorageDir, fileNameToOpen)
            if (!file.exists()) {
                Toast.makeText(context, "File not found: $fileNameToOpen", Toast.LENGTH_LONG).show()
                return
            }

            val content = file.readText()
            addToUndoStack(textFieldValue)
            textFieldValue = TextFieldValue(content)
            isModified = false

            // Update the displayed filename without extension
            fileName = fileNameToOpen.substringBeforeLast(".")

            // Detect language based on file extension
            val fileExt = fileNameToOpen.substringAfterLast(".", "")
            val detectedLanguage = languageExtensions.entries.find { it.value == fileExt }?.key ?: "Plain Text"
            selectedLanguage = detectedLanguage

            Toast.makeText(context, "Opened $fileNameToOpen", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TextEditor", "Error opening file", e)
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Display compilation output - moved before startOutputMonitoring
    fun displayOutput(output: String, fileName: String) {
        val status = when {
            output.contains("❌") || output.contains("error", ignoreCase = true) -> "COMPILATION FAILED"
            output.contains("💥") || output.contains("exception", ignoreCase = true) -> "RUNTIME ERROR"
            else -> "COMPILATION SUCCESSFUL"
        }

        compilationStatus = status
        compilationOutput = output
        showOutputDialog = true
    }

    // Output monitoring for compilation results
    fun startOutputMonitoring(fileName: String) {
        val nameOnly = fileName.substringBeforeLast(".")
        val outputFileName = "$nameOnly.txt"

        coroutineScope.launch(Dispatchers.IO) {
            // Clear any existing output file first to prevent showing stale results
            val outputFile = File(PHONE_CODE_DIR, outputFileName)
            if (outputFile.exists()) {
                try {
                    outputFile.delete()
                    Log.d("TextEditor", "Cleared previous output file: $outputFileName")
                } catch (e: Exception) {
                    Log.w("TextEditor", "Failed to clear previous output file", e)
                }
            }

            // Wait a bit before starting to monitor for new output
            delay(2000)

            repeat(30) { attempt ->
                delay(4000)
                if (outputFile.exists()) {
                    try {
                        val output = outputFile.readText()
                        // Only process if file has actual content
                        if (output.trim().isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                displayOutput(output, fileName)
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("TextEditor", "Error reading output file", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error reading output", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Compilation timeout", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Compilation function
    fun compileCode() {
        val code = textFieldValue.text
        if (code.isEmpty()) {
            Toast.makeText(context, "No code to compile", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure file is saved before compiling
        saveFile(fileName)

        try {
            val codeDir = File(PHONE_CODE_DIR)
            if (!codeDir.exists()) {
                codeDir.mkdirs()
                Log.d("CompileCode", "Created directory: $PHONE_CODE_DIR")
            }

            // Get the current filename with extension
            val extension = languageExtensions[selectedLanguage] ?: "txt"
            val currentFileNameWithExt = if (fileName.contains('.')) fileName else "$fileName.$extension"

            // Write the main code file using the current filename
            val codeFile = File(codeDir, currentFileNameWithExt)
            codeFile.writeText(code)

            // Create request.txt file containing the filename
            val requestFile = File(codeDir, "request.txt")
            requestFile.writeText(currentFileNameWithExt)

            Toast.makeText(context, "Compiling $currentFileNameWithExt...", Toast.LENGTH_SHORT).show()

            // Start output monitoring
            startOutputMonitoring(currentFileNameWithExt)

        } catch (e: Exception) {
            Log.e("TextEditor", "Compilation error", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Find and replace functions
    fun performFind() {
        if (findText.isEmpty()) {
            Toast.makeText(context, "Please enter text to find", Toast.LENGTH_SHORT).show()
            return
        }

        val text = textFieldValue.text
        val searchText = if (caseSensitive) findText else findText.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        // Find all occurrences
        val results = mutableListOf<Int>()
        var index = content.indexOf(searchText)
        while (index != -1) {
            results.add(index)
            index = content.indexOf(searchText, index + searchText.length)
        }

        findResults = results

        if (results.isEmpty()) {
            Toast.makeText(context, "Text not found", Toast.LENGTH_SHORT).show()
            currentFindIndex = -1
            return
        }

        // Find next occurrence from current position
        val cursorPos = textFieldValue.selection.start
        val nextIndex = results.find { it >= cursorPos } ?: results.first()

        // Update selection to highlight found text
        textFieldValue = textFieldValue.copy(
            selection = TextRange(nextIndex, nextIndex + findText.length)
        )

        currentFindIndex = results.indexOf(nextIndex)
        Toast.makeText(context, "Found ${results.size} occurrence(s)", Toast.LENGTH_SHORT).show()
    }

    fun findNext() {
        if (findResults.isEmpty()) {
            performFind()
            return
        }

        if (currentFindIndex == -1 || currentFindIndex >= findResults.size - 1) {
            currentFindIndex = 0
        } else {
            currentFindIndex++
        }

        val nextIndex = findResults[currentFindIndex]
        textFieldValue = textFieldValue.copy(
            selection = TextRange(nextIndex, nextIndex + findText.length)
        )
    }

    fun findPrevious() {
        if (findResults.isEmpty()) {
            performFind()
            return
        }

        if (currentFindIndex == -1 || currentFindIndex == 0) {
            currentFindIndex = findResults.size - 1
        } else {
            currentFindIndex--
        }

        val prevIndex = findResults[currentFindIndex]
        textFieldValue = textFieldValue.copy(
            selection = TextRange(prevIndex, prevIndex + findText.length)
        )
    }

    fun replaceCurrent() {
        if (findResults.isEmpty() || currentFindIndex == -1) {
            Toast.makeText(context, "No text to replace", Toast.LENGTH_SHORT).show()
            return
        }

        val currentIndex = findResults[currentFindIndex]
        val newText = textFieldValue.text.replaceRange(
            currentIndex,
            currentIndex + findText.length,
            replaceText
        )

        addToUndoStack(textFieldValue)
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(currentIndex + replaceText.length)
        )

        // Update find results after replacement
        findResults = findResults.map { index ->
            if (index > currentIndex) index + (replaceText.length - findText.length) else index
        }.filter { it != currentIndex }

        Toast.makeText(context, "Replaced occurrence", Toast.LENGTH_SHORT).show()
    }

    fun replaceAll() {
        if (findText.isEmpty()) {
            Toast.makeText(context, "Please enter text to find", Toast.LENGTH_SHORT).show()
            return
        }

        val text = textFieldValue.text
        val searchText = if (caseSensitive) findText else findText.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        val occurrences = content.split(searchText).size - 1
        if (occurrences == 0) {
            Toast.makeText(context, "Text not found", Toast.LENGTH_SHORT).show()
            return
        }

        addToUndoStack(textFieldValue)

        val newText = if (caseSensitive) {
            text.replace(findText, replaceText)
        } else {
            text.replace(findText, replaceText, ignoreCase = true)
        }

        textFieldValue = TextFieldValue(newText)
        findResults = emptyList()
        currentFindIndex = -1

        Toast.makeText(context, "Replaced $occurrences occurrence(s)", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$fileName.${languageExtensions[selectedLanguage]}" + if (isModified) " • Modified" else "",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (!keywordsLoaded) {
                        Text(
                            text = "Keywords not loaded - Basic highlighting only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { compileCode() }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Compile/Run",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolbarButton(Icons.Filled.Add, "New") {
                    addToUndoStack(textFieldValue)
                    textFieldValue = TextFieldValue("")
                    fileName = "Untitled"
                    isModified = false
                    selectedLanguage = "Plain Text"
                }
                ToolbarButton(Icons.Filled.FolderOpen, "Open") {
                    showOpenDialog = true
                }
                ToolbarButton(Icons.Filled.Save, "Save") {
                    showSaveDialog = true
                }

                HorizontalDivider(
                    modifier = Modifier.height(32.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                ToolbarButton(Icons.Filled.ContentCopy, "Copy") {
                    val selectedText = textFieldValue.getSelectedText()
                    if (selectedText.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(selectedText.text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                ToolbarButton(Icons.Filled.ContentCut, "Cut") {
                    val selectedText = textFieldValue.getSelectedText()
                    if (selectedText.isNotEmpty()) {
                        addToUndoStack(textFieldValue)
                        clipboardManager.setText(AnnotatedString(selectedText.text))
                        val newText = textFieldValue.text.removeRange(
                            textFieldValue.selection.start,
                            textFieldValue.selection.end
                        )
                        textFieldValue = textFieldValue.copy(
                            text = newText,
                            selection = TextRange(textFieldValue.selection.start)
                        )
                        Toast.makeText(context, "Cut to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                ToolbarButton(Icons.Filled.ContentPaste, "Paste") {
                    val clipText = clipboardManager.getText()?.text ?: ""
                    if (clipText.isNotEmpty()) {
                        addToUndoStack(textFieldValue)
                        val newText = textFieldValue.text.replaceRange(
                            textFieldValue.selection.start,
                            textFieldValue.selection.end,
                            clipText
                        )
                        val newCursorPos = textFieldValue.selection.start + clipText.length
                        textFieldValue = textFieldValue.copy(
                            text = newText,
                            selection = TextRange(newCursorPos)
                        )
                        Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.height(32.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                ToolbarButton(Icons.Filled.Undo, "Undo", enabled = undoStack.isNotEmpty()) { undo() }
                ToolbarButton(Icons.Filled.Redo, "Redo", enabled = redoStack.isNotEmpty()) { redo() }

                HorizontalDivider(
                    modifier = Modifier.height(32.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                // Find and Replace buttons
                ToolbarButton(Icons.Filled.Search, "Find") {
                    showFindDialog = true
                }
                ToolbarButton(Icons.Filled.FindReplace, "Replace") {
                    showReplaceDialog = true
                }

                HorizontalDivider(
                    modifier = Modifier.height(32.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                ToolbarButton(Icons.Filled.Refresh, "Reload Keywords") {
                    try {
                        SyntaxHighlighter.loadKeywordsFromXML(context, R.xml.keywords_all_languages)
                        keywordsLoaded = true
                        Toast.makeText(context, "Keywords reloaded successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("TextEditor", "Failed to reload keywords", e)
                        Toast.makeText(context, "Failed to reload keywords", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Editor
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            SelectionContainer {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        if (newValue.text != textFieldValue.text) {
                            addToUndoStack(textFieldValue)
                        }
                        textFieldValue = newValue
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFD4D4D4)
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    "Start typing your ${selectedLanguage.lowercase()} code here...\n\n" +
                                            if (!keywordsLoaded) "Note: Place keywords_all_languages.xml in res/raw/ for syntax highlighting" else "",
                                    color = Color(0xFF6A6A6A),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    },
                    visualTransformation = { text ->
                        if (selectedLanguage != "Plain Text" && keywordsLoaded) {
                            TransformedText(
                                SyntaxHighlighter.highlightSyntax(text.text, selectedLanguage),
                                OffsetMapping.Identity
                            )
                        } else {
                            TransformedText(
                                AnnotatedString(text.text, SpanStyle(color = Color(0xFFD4D4D4))),
                                OffsetMapping.Identity
                            )
                        }
                    }
                )
            }
        }

        // Status Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Status and Language
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isModified) Icons.Filled.Edit else Icons.Filled.CheckCircle,
                        contentDescription = "Status",
                        tint = if (isModified) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (isModified) "Modified" else "Saved",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Keywords status indicator
                    Icon(
                        if (keywordsLoaded) Icons.Filled.Check else Icons.Filled.Warning,
                        contentDescription = "Keywords Status",
                        tint = if (keywordsLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        if (keywordsLoaded) "Syntax Ready" else "No Keywords",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Find results indicator
                    if (findResults.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Find Results",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "${currentFindIndex + 1}/${findResults.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Language dropdown
                    Box(modifier = Modifier.zIndex(10f)) {
                        FilledTonalButton(
                            onClick = { expandedLanguage = true },
                            modifier = Modifier
                                .height(28.dp)
                                .padding(horizontal = 4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    selectedLanguage,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedLanguage,
                            onDismissRequest = { expandedLanguage = false },
                            modifier = Modifier.zIndex(11f)
                        ) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(lang)
                                            if (lang != "Plain Text" && keywordsLoaded &&
                                                SyntaxHighlighter.getKeywordsForLanguage(lang).isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "Supported",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedLanguage = lang
                                        expandedLanguage = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Row 2: Statistics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusItem("Ln", currentLine.toString())
                    StatusItem("Col", currentColumn.toString())
                    StatusItem("Lines", lineCount.toString())
                    StatusItem("Words", wordCount.toString())
                    StatusItem("Chars", charCount.toString())
                }
            }
        }
    }

    // Save Dialog
    if (showSaveDialog) {
        Dialog(onDismissRequest = { showSaveDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Save Document",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter filename (e.g., hello.c)") }
                    )
                    Text(
                        "File will be saved with extension: .${languageExtensions[selectedLanguage]}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            saveFile(fileName)
                            showSaveDialog = false
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Open File Dialog
    if (showOpenDialog) {
        AlertDialog(
            onDismissRequest = { showOpenDialog = false },
            title = { Text("Select a file") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (fileList.isEmpty()) {
                        Text(
                            "No files found in storage directory",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        fileList.forEach { fileNameItem ->
                            Text(
                                text = fileNameItem,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        openFile(fileNameItem)
                                        showOpenDialog = false
                                    }
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOpenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Find Dialog
    if (showFindDialog) {
        Dialog(onDismissRequest = { showFindDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Find Text",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Text to find") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter text to search") }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it }
                        )
                        Text(
                            "Case sensitive",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { caseSensitive = !caseSensitive }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { performFind() },
                            enabled = findText.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Find")
                        }

                        IconButton(
                            onClick = { findPrevious() },
                            enabled = findResults.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Previous")
                        }

                        IconButton(
                            onClick = { findNext() },
                            enabled = findResults.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Next")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showFindDialog = false
                            showReplaceDialog = true
                        }) {
                            Text("Replace...")
                        }
                        TextButton(onClick = { showFindDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    // Replace Dialog
    if (showReplaceDialog) {
        Dialog(onDismissRequest = { showReplaceDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Find and Replace",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Text to find") }
                    )

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Replacement text") }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it }
                        )
                        Text(
                            "Case sensitive",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { caseSensitive = !caseSensitive }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { performFind() },
                            enabled = findText.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Find")
                        }

                        IconButton(
                            onClick = { findPrevious() },
                            enabled = findResults.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Previous")
                        }

                        IconButton(
                            onClick = { findNext() },
                            enabled = findResults.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Next")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { replaceCurrent() },
                            enabled = findResults.isNotEmpty() && currentFindIndex != -1,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Replace")
                        }

                        Button(
                            onClick = { replaceAll() },
                            enabled = findText.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Replace All")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showReplaceDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    // Output Dialog
    if (showOutputDialog) {
        AlertDialog(
            onDismissRequest = { showOutputDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (compilationStatus.contains("FAILED") || compilationStatus.contains("ERROR")) {
                            Icons.Default.Error
                        } else {
                            Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = if (compilationStatus.contains("FAILED") || compilationStatus.contains("ERROR")) {
                            Color.Red
                        } else {
                            Color(0xFF4CAF50) // Green
                        }
                    )
                    Text(
                        text = compilationStatus,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 300.dp)
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = compilationOutput,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFFDCDCDC)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showOutputDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

}

@Composable
fun ToolbarButton(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = text,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
fun StatusItem(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}