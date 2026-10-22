package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.zhzgo.study.ui.components.*

@Composable
fun ExerciseResultScreen(subjectId:Int,subjectName:String,score:Int,maxScore:Int,correctCount:Int,incorrectCount:Int,
    unansweredCount:Int,onBack:()->Unit,onRetry:()->Unit,onReviewMistakes:()->Unit,durationSeconds:Int=0) {
    val answered=correctCount+incorrectCount
    val accuracy=if(answered>0)correctCount*100/answered else 0
    StudyPage("答题结果",onBack){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=26.dp,bottom=24.dp)) {
            item {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    ProgressRing(if(maxScore>0)score.toFloat()/maxScore else 0f,"$score","分","总分 $maxScore",184.dp,Color(0xFF68CDB6))
                }
                Spacer(Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth()) {
                    ResultCount("$correctCount","正确",Color(0xFF54BDA5),Modifier.weight(1f))
                    ResultCount("$incorrectCount","错误",Color(0xFFDF6879),Modifier.weight(1f))
                    ResultCount("$unansweredCount","未做",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.weight(1f))
                }
                Spacer(Modifier.height(34.dp))
                Row(Modifier.fillMaxWidth()) {
                    ResultCount(if(durationSeconds>0)"${durationSeconds/60}分${durationSeconds%60}秒" else "—","本次用时",MaterialTheme.colorScheme.onSurface,Modifier.weight(1f))
                    ResultCount("$answered 题","答题数量",MaterialTheme.colorScheme.onSurface,Modifier.weight(1f))
                    ResultCount(if(answered>0)"$accuracy%" else "—","正确率",MaterialTheme.colorScheme.onSurface,Modifier.weight(1f))
                }
                Text("仅统计本次加载题目，科目进度会保留。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.padding(top=22.dp,bottom=30.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick=onRetry,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=MaterialTheme.shapes.medium){Text("返回练习")}
                    Button(onClick=onReviewMistakes,enabled=incorrectCount>0,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=MaterialTheme.shapes.medium,
                        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.onSurface,contentColor=MaterialTheme.colorScheme.surface)){Text("错题重练")}
                }
                TextButton(onClick=onBack,modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("完成")}
            }
        }
    }
}

@Composable
private fun ResultCount(value:String,label:String,color:Color,modifier:Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(value,fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=color)
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
