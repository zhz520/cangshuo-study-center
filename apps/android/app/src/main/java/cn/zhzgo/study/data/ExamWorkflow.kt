package cn.zhzgo.study.data
object ExamWorkflow {
    fun remainingSeconds(deadline:Long,now:Long):Int = ((deadline-now).coerceAtLeast(0L)/1000L + if((deadline-now).coerceAtLeast(0L)%1000L>0)1 else 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    fun answeredIds(questionIds:List<Int>,answers:Map<String,String>):Set<Int> = questionIds.filter{!answers[it.toString()].isNullOrBlank()}.toSet()
    fun toggleMultiple(raw:String,option:String):String {
        val selected=raw.split(",").map{it.trim()}.filter{it.isNotEmpty()}.toMutableSet()
        if(!selected.add(option))selected.remove(option)
        return selected.sorted().joinToString(",")
    }
}
