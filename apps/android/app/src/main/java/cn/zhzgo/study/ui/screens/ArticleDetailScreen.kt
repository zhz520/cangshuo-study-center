package cn.zhzgo.study.ui.screens
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.Article
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.ui.components.*
import cn.zhzgo.study.ui.viewmodels.ArticleDetailViewModel
@Composable
fun ArticleDetailScreen(articleId:Int,onBack:()->Unit,onUserClick:(String)->Unit={},onOpenLink:(String,String)->Unit={_,_->}) {
    val context=LocalContext.current;val model:ArticleDetailViewModel=viewModel()
    val article by model.article.collectAsState();val comments by model.comments.collectAsState();val loading by model.isLoading.collectAsState()
    val error by model.error.collectAsState();val commentError by model.commentError.collectAsState();val posting by model.isPosting.collectAsState();val deleting by model.deleting.collectAsState()
    val prefs=remember{UserPreferences(context)};val email by prefs.email.collectAsState(initial=null);val username by prefs.userName.collectAsState(initial=null);val role by prefs.userRole.collectAsState(initial=null)
    val clipboard=LocalClipboardManager.current
    var text by rememberSaveable(articleId){mutableStateOf("")};var font by rememberSaveable{mutableIntStateOf(16)}
    var menu by remember{mutableStateOf(false)};var image by remember{mutableStateOf<String?>(null)};var delete by remember{mutableStateOf<Int?>(null)}
    LaunchedEffect(articleId){model.fetchArticleDetail(context,articleId)}
    StudyPage("阅读",onBack,actions={
        Box {
            IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreHoriz,"阅读操作")}
            DropdownMenu(menu,{menu=false}) {
                DropdownMenuItem(text={Text("字号：${font}sp")},onClick={font=if(font>=20)16 else font+2;menu=false})
                DropdownMenuItem(text={Text("复制链接")},onClick={clipboard.setText(AnnotatedString("https://study.zhzgo.cn/article/$articleId"));menu=false})
                DropdownMenuItem(text={Text("分享文章")},onClick={menu=false;context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"${article?.title.orEmpty()} https://study.zhzgo.cn/article/$articleId")},"分享文章"))})
            }
        }
    },bottomBar={if(article!=null)Surface {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text,{text=it},Modifier.weight(1f),placeholder={Text("写下你的想法")},maxLines=4,enabled=!posting,shape=MaterialTheme.shapes.medium)
                IconButton(onClick={if(email.isNullOrBlank())android.widget.Toast.makeText(context,"请先绑定邮箱，以接收评论回复",android.widget.Toast.LENGTH_LONG).show()else model.postComment(context,articleId,text){text=""}},enabled=text.isNotBlank()&&!posting){Icon(Icons.AutoMirrored.Filled.Send,if(posting)"正在发送" else "发送评论")}
            }
        }
    }}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            if(loading)item{cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())}
            error?.let{message->item{StateMessage("文章暂不可读",message,"重试",{model.fetchArticleDetail(context,articleId)})}}
            article?.let{a->
                item {
                    Text(a.title,style=MaterialTheme.typography.headlineMedium)
                    Text(listOfNotNull(a.category,a.getTime()?.take(10),a.view_count?.let{"$it 次阅读"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp))
                    TextButton(onClick={a.author_id?.let{onUserClick(it.toString())}},enabled=a.author_id!=null,contentPadding=PaddingValues(0.dp)){Text(a.author_name ?: "作者",style=MaterialTheme.typography.bodySmall)}
                    if(!a.summary.isNullOrBlank())Text(a.summary,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
                    Spacer(Modifier.height(12.dp));ArticleContentWeb(a,font,onOpenLink){image=it}
                    Spacer(Modifier.height(24.dp));SectionLabel("讨论","${comments.size} 条")
                    commentError?.let{StateMessage("评论操作未完成",it,"刷新评论",{model.fetchComments(context,articleId)})}
                    if(comments.isEmpty())Text("还没有评论，欢迎分享你的想法。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(comments,key={it.id}){c->
                    Column(Modifier.padding(vertical=14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(c.username,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f).clickable{onUserClick(c.user_id.toString())}.heightIn(min=44.dp).wrapContentHeight())
                            Text(c.created_at.take(10),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(c.username==username || role=="admin")IconButton(onClick={delete=c.id},enabled=c.id !in deleting){Icon(Icons.Outlined.DeleteOutline,"删除评论",Modifier.size(18.dp))}
                        }
                        Text(c.content,style=MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if(delete!=null)AlertDialog(onDismissRequest={delete=null},title={Text("删除这条评论？")},confirmButton={TextButton(onClick={delete?.let{model.deleteComment(context,it,articleId)};delete=null}){Text("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={delete=null}){Text("取消")}})
    image?.let{ImageViewerDialog(it){image=null}}
}
@Composable
fun ArticleContentWeb(article:Article,fontSize:Int,onOpenLink:(String,String)->Unit,onImageClick:((String)->Unit)?=null) {
    val content=if(article.content_format=="markdown")article.content_markdown.orEmpty()else article.content.orEmpty()
    MathText(content,fontSize=fontSize.sp,onImageClick=onImageClick,onLinkClick={onOpenLink(it,article.title)})
}
