package cn.zhzgo.study.data
import com.google.gson.Gson
/** Legacy APIs can send a JSON string, a map, a string array or labeled option objects. */
object QuestionOptions {
    fun parse(value:Any?):Map<String,String> = try {
        when(value) {
            is String -> if(value.isBlank())emptyMap()else Gson().fromJson(value,Any::class.java).let{if(it is String)emptyMap()else parse(it)}
            is Map<*,*> -> value.entries.filter{it.key!=null&&it.value!=null}.associate{it.key.toString() to it.value.toString()}
            is List<*> -> value.mapIndexedNotNull{index,item->
                if(item==null)null else if(item is Map<*,*>) {
                    val label=item["label"]?.toString() ?: ('A'.code+index).toChar().toString()
                    label to (item["content"] ?: item["text"] ?: "").toString()
                }else ('A'.code+index).toChar().toString() to item.toString()
            }.toMap()
            else -> emptyMap()
        }
    }catch(e:Exception){emptyMap()}
}
