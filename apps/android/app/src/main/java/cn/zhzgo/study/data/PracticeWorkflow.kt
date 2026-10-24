package cn.zhzgo.study.data
/** Pure boundary rules shared by navigation, persistence and practice loading. */
object PracticeWorkflow {
    fun sheetPageCount(count:Int,pageSize:Int=50):Int { require(pageSize>0);return ((count.coerceAtLeast(0)+pageSize-1)/pageSize).coerceAtLeast(1) }
    fun sheetIndices(count:Int,page:Int,pageSize:Int=50):List<Int> {
        require(pageSize>0)
        val start=page.coerceIn(0,sheetPageCount(count,pageSize)-1)*pageSize
        return (start until minOf(start+pageSize,count)).toList()
    }
    fun pageForIndex(index:Int,pageSize:Int=50):Int {
        require(pageSize>0)
        return index.coerceIn(0,1_000_000)/pageSize+1
    }
    fun reviewQueue(ids:List<Int>):List<Int> = ids.filter{it>0}.distinct().take(20)
    fun restoreDraft(type:String,raw:String):Any = if(type=="multiple") raw.split(",").filter{it.isNotBlank()}.toSet() else raw
}
