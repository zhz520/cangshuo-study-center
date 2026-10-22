package cn.zhzgo.study.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import cn.zhzgo.study.ui.theme.SuccessColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.image.ImagesPlugin
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import cn.zhzgo.study.data.Question

// Minimalist Theme Colors
val ChatGPTBlack = Color(0xFF202123)
val ChatGPTGray = Color(0xFF343541)
val ChatGPTLightGray = Color(0xFF444654)
val ChatGPTText = Color.White
val ChatGPTTextSecondary = Color(0xFFACACBE)

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground, fontSize: TextUnit = 16.sp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(io.noties.markwon.html.HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(48f)) // Use consistent font size
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(android.graphics.Color.argb(
                    (color.alpha * 255).toInt(),
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                ))
                textSize = fontSize.value
            }
        },
        update = { textView ->
            textView.textSize = fontSize.value
            textView.setTextColor(android.graphics.Color.argb((color.alpha*255).toInt(),(color.red*255).toInt(),(color.green*255).toInt(),(color.blue*255).toInt()))
            textView.setLineSpacing(0f, 1.4f)
            var processedText = text.replace("\\n", "\n")
                .replace("](/uploads/", "](https://studyapi.zhzgo.cn/uploads/")
                .replace("src=\"/uploads/", "src=\"https://studyapi.zhzgo.cn/uploads/")

            // 1. Normalize block delimiters \[ ... \] -> $$ ... $$
            processedText = processedText.replace(Regex("""\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL)) {
                "\n$$\n" + it.groupValues[1].trim() + "\n$$\n"
            }

            // 2. Normalize inline delimiters \( ... \) -> $ ... $
            processedText = processedText.replace(Regex("""\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL)) {
                "$" + it.groupValues[1].trim() + "$"
            }

            // 3. Fix \begin{env} ... \end{env} blocks
            val envs = "align|equation|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|cases|gather|flalign|alignat|multline"
            val envRegex = Regex("""\\begin\{($envs)\*?\}(.*?)\\end\{\1\*?\}""", RegexOption.DOT_MATCHES_ALL)
            processedText = processedText.replace(envRegex) {
                "\n$$\n" + it.value.trim() + "\n$$\n"
            }

            // 4. Force single $ ... $ to be treated as math blocks
            val inlineFormulaRegex = Regex("""(?<!\$)\$(?!\s)([^$\n]+?)(?<!\s)\$(?!\$)""")
            processedText = processedText.replace(inlineFormulaRegex) {
                "$$" + it.groupValues[1].trim() + "$$"
            }

            markwon.setMarkdown(textView, processedText)
        }
    )
}

@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fontSize: TextUnit = 16.sp,
    onHeightChange: ((Int) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var webViewHeight by remember { mutableStateOf(100) }

    fun Color.toHex(): String = String.format("#%02x%02x%02x",
        (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

    val textColorHex = color.toHex()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgColorHex = "transparent"
    val scaledSize = fontSize.value * LocalDensity.current.fontScale

    // Process text for initial display (simple replacements)
    val initialText = text.replace("\n", "<br>")
        .replace("](/uploads/", "](https://studyapi.zhzgo.cn/uploads/")
        .replace("src=\"/uploads/", "src=\"https://studyapi.zhzgo.cn/uploads/")

    // Base64 version for full rendering
    val base64Text = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="file:///android_asset/katex/katex.min.css">
            <style>
                body {
                    font-family: -apple-system, system-ui, Roboto, sans-serif;
                    font-size: ${scaledSize}px;
                    color: $textColorHex;
                    line-height: 1.8;
                    margin: 0;
                    padding: 8px 0;
                    background-color: $bgColorHex;
                    word-wrap: break-word;
                    overflow-x: hidden;
                    text-rendering: optimizeLegibility;
                    -webkit-user-select: none;
                    opacity: 0;
                    transition: opacity 0.2s ease-in;
                }
                body.math-rendered {
                    opacity: 1;
                }
                #container {
                    width: 100%;
                    min-height: 20px;
                    display: block;
                }
                .katex-display {
                    margin: 1.2em 0;
                    overflow-x: auto;
                    overflow-y: hidden;
                    padding: 4px 0;
                }
                .katex { font-size: 1.1em; }
                img { max-width: 100%; height: auto; border-radius: 4px; margin: 12px 0; display: block; cursor: pointer; }
                h1, h2, h3, h4 { margin: 20px 0 10px 0; line-height: 1.4; }
                p { margin: 10px 0; }
                ul, ol { padding-left: 20px; margin: 10px 0; }
                li { margin: 6px 0; }
                pre { overflow-x: auto; padding: 12px; background: rgba(128,128,128,.08); border-radius: 10px; font-size: .9em; }
                code { font-family: ui-monospace, monospace; }
                table { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
                th, td { padding: 8px 10px; border: 1px solid rgba(128,128,128,.25); }
                blockquote { border-left: 2px solid rgba(128,128,128,.4); margin: 16px 0; padding-left: 14px; }
            </style>
        </head>
        <body>
            <div id="container"></div>

            <script src="file:///android_asset/katex/katex.min.js"></script>
            <script src="file:///android_asset/katex/contrib/auto-render.min.js"></script>
            <script src="file:///android_asset/marked/marked.min.js"></script>
            <script src="file:///android_asset/sanitize/purify.min.js"></script>

            <script>
                function updateHeight() {
                    try {
                        const container = document.getElementById('container');
                        const height = Math.max(container.scrollHeight, document.body.scrollHeight, document.documentElement.scrollHeight, 40);
                        Android.onHeightChange(height);
                    } catch(e) {}
                }

                function b64_to_utf8(str) {
                    try {
                        const binaryString = window.atob(str);
                        const bytes = new Uint8Array(binaryString.length);
                        for (let i = 0; i < binaryString.length; i++) {
                            bytes[i] = binaryString.charCodeAt(i);
                        }
                        return new TextDecoder('utf-8').decode(bytes);
                    } catch (e) {
                        return decodeURIComponent(escape(window.atob(str)));
                    }
                }

                function performFullRender() {
                    try {
                        const container = document.getElementById('container');
                        let rawText = b64_to_utf8('$base64Text');

                        // Fix for KaTeX: replace \begin{align} with \begin{aligned} etc
                        rawText = rawText.replace(/\\begin\{align\*?\}/g, "\\begin{aligned}")
                                         .replace(/\\end\{align\*?\}/g, "\\end{aligned}");
                        rawText = rawText.replace(/\\begin\{eqnarray\*?\}/g, "\\begin{aligned}")
                                         .replace(/\\end\{eqnarray\*?\}/g, "\\end{aligned}");
                        rawText = rawText.replace(/\\begin\{gather\*?\}/g, "\\begin{gathered}")
                                         .replace(/\\end\{gather\*?\}/g, "\\end{gathered}");

                        // Mask math blocks to prevent marked from parsing them and eating backslashes
                        let mathBlocks = [];
                        let textToMark = rawText;

                        // Mask $$ ... $$
                        textToMark = textToMark.replace(/\$\$([\s\S]*?)\$\$/g, function(match) {
                            mathBlocks.push(match);
                            return 'MATH_BLK_' + (mathBlocks.length - 1) + '_';
                        });

                        // Mask \[ ... \]
                        textToMark = textToMark.replace(/\\\[([\s\S]*?)\\\]/g, function(match) {
                            mathBlocks.push(match);
                            return 'MATH_BLK_' + (mathBlocks.length - 1) + '_';
                        });

                        // Mask \( ... \)
                        textToMark = textToMark.replace(/\\\(([\s\S]*?)\\\)/g, function(match) {
                            mathBlocks.push(match);
                            return 'MATH_BLK_' + (mathBlocks.length - 1) + '_';
                        });

                        // Mask $ ... $ (inline)
                        textToMark = textToMark.replace(/\$((?:[^$\\]|\\.)+)\$/g, function(match) {
                            mathBlocks.push(match);
                            return 'MATH_BLK_' + (mathBlocks.length - 1) + '_';
                        });

                        if (typeof marked !== 'undefined') {
                            marked.setOptions({
                                breaks: true,
                                gfm: true,
                                headerIds: false,
                                mangle: false
                            });
                            textToMark = marked.parse(textToMark);
                        }

                        // Restore math blocks
                        textToMark = textToMark.replace(/MATH_BLK_(\d+)_/g, function(match, p1) {
                            return mathBlocks[parseInt(p1)];
                        });

                        container.innerHTML = DOMPurify.sanitize(textToMark, {USE_PROFILES: {html: true}, FORBID_TAGS: ['style', 'form', 'input', 'button'], FORBID_ATTR: ['style']});

                        if (typeof renderMathInElement !== 'undefined') {
                            renderMathInElement(container, {
                                delimiters: [
                                    {left: "$$", right: "$$", display: true},
                                    {left: "$", right: "$", display: false},
                                    {left: "\\(", right: "\\)", display: false},
                                    {left: "\\[", right: "\\]", display: true},
                                    {left: "\\begin{aligned}", right: "\\end{aligned}", display: true},
                                    {left: "\\begin{gathered}", right: "\\end{gathered}", display: true},
                                    {left: "\\begin{equation}", right: "\\end{equation}", display: true},
                                    {left: "\\begin{matrix}", right: "\\end{matrix}", display: true},
                                    {left: "\\begin{cases}", right: "\\end{cases}", display: true}
                                ],
                                throwOnError: false
                            });
                        }

                        document.body.classList.add('math-rendered');

                        setTimeout(updateHeight, 100);
                        setTimeout(updateHeight, 500);
                        setTimeout(updateHeight, 1000);

                        const imgs = document.getElementsByTagName('img');
                        for(let i=0; i<imgs.length; i++) {
                            imgs[i].addEventListener('load', updateHeight);
                            imgs[i].addEventListener('click', function() {
                                try { Android.onImageClick(this.src); } catch(e) {}
                            });
                        }
                    } catch (e) {
                        console.error("Render error:", e);
                        updateHeight();
                    }
                }

                updateHeight();

                if (document.readyState === 'complete') {
                    performFullRender();
                } else {
                    window.addEventListener('load', performFullRender);
                    // Backup trigger for fast-loading assets
                    setTimeout(performFullRender, 100);
                }

                if (window.ResizeObserver) {
                    new ResizeObserver(updateHeight).observe(document.getElementById('container'));
                }
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(webViewHeight.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                settings.textZoom = 100
                isClickable = true
                isFocusable = true
                // Disable built-in long-press image handling that can block click events
                setOnLongClickListener { true }

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onHeightChange(height: Float) {
                        // JavaScript JS pixels map 1:1 to Android DP if viewport width=device-width
                        val heightDp = height.toInt() + 4
                        post {
                            if (webViewHeight != heightDp) {
                                webViewHeight = heightDp
                            }
                            onHeightChange?.invoke(heightDp)
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun onImageClick(url: String) {
                        post { onImageClick?.invoke(url) }
                    }
                }, "Android")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        if(request.hasGesture() && request.url.scheme == "https") {
                            if(onLinkClick!=null)onLinkClick(request.url.toString())
                            else runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,request.url)) }
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if(webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL("https://studyapi.zhzgo.cn/", htmlContent, "text/html", "UTF-8", null)
            }
        }
    )
}

@Composable
fun RenderText(
    text: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fontSize: TextUnit = 16.sp,
    onImageClick: ((String) -> Unit)? = null
) {
    if (text == null) return

    // Check if it contains math markers or images
    val hasMath = text.contains("$") || text.contains("\\") || text.contains("{")
    val hasImages = text.contains("<img") || text.contains("![") || text.contains("/uploads/")

    if (hasMath || hasImages) {
        MathText(text = text, modifier = modifier, color = color, fontSize = fontSize, onImageClick = onImageClick)
    } else {
        MarkdownText(text = text, modifier = modifier, color = color, fontSize = fontSize)
    }
}

@Composable
fun QuestionContent(question: Question, onImageClick: ((String) -> Unit)? = null) {
    RenderText(
        text = question.content,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 24.dp),
        onImageClick = onImageClick
    )
}

@Composable
private fun AnswerOption(key: String, text: String, selected: Boolean, correct: Boolean, showAnswer: Boolean,
    multiple: Boolean = false, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val success = if(colors.background.luminance()<.5f) Color(0xFF7DDBA2) else SuccessColor
    val accent = when { showAnswer && correct -> success; showAnswer && selected -> colors.error; selected -> colors.primary; else -> colors.outlineVariant }
    val state = when { showAnswer && correct -> "正确答案"; showAnswer && selected -> "回答错误"; selected -> "已选"; else -> "未选" }
    Row(Modifier.fillMaxWidth().heightIn(min=50.dp).clip(RoundedCornerShape(9.dp))
        .background(if(selected || (showAnswer && correct)) accent.copy(alpha=.08f) else colors.surface)
        .border(1.dp,accent,RoundedCornerShape(9.dp))
        .selectable(selected=selected,enabled=!showAnswer,role=if(multiple)Role.Checkbox else Role.RadioButton,onClick=onSelect)
        .semantics { stateDescription=state }.padding(horizontal=14.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp).background(if(selected || (showAnswer && correct)) accent else Color.Transparent,
            if(multiple)RoundedCornerShape(6.dp)else CircleShape).border(1.dp,accent.copy(alpha=if(selected||showAnswer&&correct)1f else .45f),if(multiple)RoundedCornerShape(6.dp)else CircleShape),contentAlignment=Alignment.Center) {
            if(showAnswer && (correct || selected)) Icon(if(correct)Icons.Default.Check else Icons.Default.Close,null,Modifier.size(16.dp),tint=if(accent.luminance()>.45f)Color.Black else Color.White)
            else Text(key,style=MaterialTheme.typography.labelLarge,color=if(selected)colors.onPrimary else colors.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            RenderText(text,fontSize=16.sp,color=colors.onSurface)
            if(showAnswer && (correct || selected)) Text(state,style=MaterialTheme.typography.labelSmall,color=accent)
        }
    }
}
@Composable
fun SingleChoiceQuestion(options:Map<String,String>,selectedOption:String?,onOptionSelected:(String)->Unit,showAnswer:Boolean,correctAnswer:String?) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { options.forEach { (key,value) ->
        AnswerOption(key,value,selectedOption==key,correctAnswer?.trim()?.uppercase()==key.uppercase(),showAnswer,onSelect={onOptionSelected(key)})
    } }
}
@Composable
fun MultiChoiceQuestion(options:Map<String,String>,selectedOptions:Set<String>,onOptionToggle:(String)->Unit,showAnswer:Boolean,correctAnswer:String?) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { options.forEach { (key,value) ->
        AnswerOption(key,value,key in selectedOptions,correctAnswer?.uppercase()?.contains(key.uppercase())==true,showAnswer,true){onOptionToggle(key)}
    } }
}
@Composable
fun JudgeQuestion(selectedOption:String?,onOptionSelected:(String)->Unit,showAnswer:Boolean,correctAnswer:String?) {
    SingleChoiceQuestion(linkedMapOf("A" to "正确","B" to "错误"),selectedOption,onOptionSelected,showAnswer,correctAnswer)
}
@Composable
fun FillBlankQuestion(answerText:String,onAnswerChange:(String)->Unit,showAnswer:Boolean,correctAnswer:String?) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value=answerText,onValueChange=onAnswerChange,modifier=Modifier.fillMaxWidth(),
            readOnly=showAnswer,label={Text("填写答案")},textStyle=MaterialTheme.typography.bodyLarge,shape=RoundedCornerShape(12.dp))
        if(showAnswer)Text("参考答案：${correctAnswer.orEmpty()}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
    }
}
@Composable
fun AnswerSheetDrawer(questions:List<Question>,currentIndex:Int,answers:Map<Int,Any>,onQuestionClick:(Int)->Unit,
    isPlus:Boolean=true,freeLimit:Int=20,submitted:Set<Int>?=null,marked:Set<Int> = emptySet(),offset:Int=0,onClose:(()->Unit)?=null) {
    val colors=MaterialTheme.colorScheme
    val answered=questions.filter { q -> submitted?.contains(q.id) ?: when(val a=answers[q.id]) {is String->a.isNotBlank();is Collection<*>->a.isNotEmpty();else->false} }.map{it.id}.toSet()
    val groups=questions.indices.groupBy{questions[it].type}
    var expanded by remember(groups.keys){mutableStateOf(setOfNotNull(questions.getOrNull(currentIndex)?.type ?: groups.keys.firstOrNull()))}
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("答题卡",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            onClose?.let{close->IconButton(onClick=close){Icon(Icons.Default.Close,"关闭答题卡",Modifier.size(21.dp))}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.padding(top=8.dp,bottom=18.dp)) {
            listOf("当前" to colors.primary,"已答" to Color(0xFF65C7B2),"未答" to colors.outlineVariant,"标记" to Color(0xFFEB8291)).forEach{(label,color)->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).background(color,CircleShape));Text(label,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=430.dp)) {
            val columns=(maxWidth.value/52).toInt().coerceAtLeast(1)
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                groups.forEach{(type,indices)->
                    item(key=type) {
                        Row(Modifier.fillMaxWidth().clickable{expanded=if(type in expanded)expanded-type else expanded+type}.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("${questionTypeLabel(type)}题（${indices.size}）",style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                            Text(if(type in expanded)"⌃" else "⌄",color=colors.onSurfaceVariant)
                        }
                    }
                    if(type in expanded) {
                        indices.chunked(columns).forEach{row->item(key="${type}_${row.first()}") {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach{i->
                                    val q=questions[i];val current=i==currentIndex;val done=q.id in answered;val flag=q.id in marked
                                    val color=when{current->colors.primary;flag->Color(0xFFEB8291);done->Color(0xFF65C7B2);else->colors.surfaceVariant}
                                    Box(Modifier.weight(1f).heightIn(min=44.dp).clip(RoundedCornerShape(7.dp))
                                        .background(if(current)color else color.copy(alpha=if(flag||done).20f else .4f))
                                        .clickable(onClickLabel="第 ${i+offset+1} 题，${if(done)"已答" else "未答"}${if(flag)"，已标记" else ""}"){onQuestionClick(i)},contentAlignment=Alignment.Center) {
                                        Text("${i+offset+1}",style=MaterialTheme.typography.bodySmall,color=if(current)colors.onPrimary else if(flag)Color(0xFFD75468) else colors.onSurface)
                                    }
                                }
                                repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                            }
                        }}
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AIAnalysisView(analysis: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "💡 解析", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        RenderText(
            text = analysis ?: "暂无解析",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0

        val mainAxisSpacingPx = mainAxisSpacing.roundToPx()
        val crossAxisSpacingPx = crossAxisSpacing.roundToPx()

        for (measurable in measurables) {
            val placeable = measurable.measure(constraints)
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                rowWidths.add(currentRowWidth)
                rowHeights.add(currentRowHeight)
                currentRow = mutableListOf()
                currentRowWidth = 0
                currentRowHeight = 0
            }
            if (currentRow.isNotEmpty()) {
                currentRowWidth += mainAxisSpacingPx
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width
            currentRowHeight = maxOf(currentRowHeight, placeable.height)
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentRowWidth)
            rowHeights.add(currentRowHeight)
        }

        val width = rowWidths.maxOrNull() ?: 0
        val height = rowHeights.sum() + (if (rows.isNotEmpty()) (rows.size - 1) * crossAxisSpacingPx else 0)

        layout(width, height) {
            var y = 0
            for ((i, row) in rows.withIndex()) {
                var x = 0
                for (placeable in row) {
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacingPx
                }
                y += rowHeights[i] + crossAxisSpacingPx
            }
        }
    }
}
