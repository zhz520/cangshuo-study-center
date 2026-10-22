package cn.zhzgo.study.data
/** Pure boundary rules shared by navigation, persistence and practice loading. */
object PracticeWorkflow {
    fun pageForIndex(index:Int,pageSize:Int=50):Int {
        require(pageSize>0)
        return index.coerceIn(0,1_000_000)/pageSize+1
    }
    fun reviewQueue(ids:List<Int>):List<Int> = ids.filter{it>0}.distinct().take(20)
    fun restoreDraft(type:String,raw:String):Any = if(type=="multiple") raw.split(",").filter{it.isNotBlank()}.toSet() else raw
}
