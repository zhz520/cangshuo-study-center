<template>
  <div class="p-4 sm:p-6 space-y-5">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white/50 dark:bg-slate-800/50 p-5 sm:p-6 rounded-3xl backdrop-blur-xl border border-white/20 dark:border-slate-700/50 shadow-sm">
      <div>
        <h2 class="text-xl sm:text-2xl font-black text-slate-800 dark:text-white tracking-tight flex items-center gap-2">
          <span>🔐</span> 题库访问控制
        </h2>
        <p class="text-xs text-slate-500 font-bold mt-1">配置用户对专业分类（Majors）的访问权限</p>
      </div>
      <div class="flex gap-2 w-full sm:w-auto">
        <button 
          @click="fetchData" 
          class="flex-1 sm:flex-none px-4 py-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-2xl text-xs font-black transition-all flex items-center justify-center gap-2"
        >
          <span>🔄</span> 刷新
        </button>
        <button 
          @click="saveChanges" 
          :disabled="!hasChanges"
          class="flex-1 sm:flex-none px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-30 disabled:grayscale text-white rounded-2xl text-xs font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all flex items-center justify-center gap-2"
        >
          <span>💾</span> 保存更改
        </button>
      </div>
    </div>

    <!-- Search / Stats Bar -->
    <div class="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
      <div class="relative flex-1">
        <input 
          v-model="searchQuery" 
          placeholder="搜索用户名..." 
          class="w-full pl-9 pr-4 py-2.5 bg-white/70 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-2xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none transition-all placeholder:font-normal hover:border-indigo-300 dark:hover:border-indigo-600 backdrop-blur-sm"
        >
        <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">🔍</span>
      </div>
      <div class="flex items-center gap-3 text-xs font-bold text-slate-400 px-1">
        <span>👤 <b class="text-indigo-600 dark:text-indigo-400">{{ filteredUsers.length }}</b> 用户</span>
        <span class="text-slate-200 dark:text-slate-700">|</span>
        <span>📚 <b class="text-indigo-600 dark:text-indigo-400">{{ majors.length }}</b> 分类</span>
        <Transition name="fade">
          <span v-if="hasChanges" class="text-amber-500 animate-pulse">● 未保存</span>
        </Transition>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="glass-card p-20 flex flex-col items-center justify-center text-slate-400 animate-pulse">
      <div class="w-10 h-10 rounded-full border-4 border-slate-100 border-t-indigo-500 animate-spin mb-4"></div>
      <span class="text-[10px] font-black tracking-widest uppercase">Loading Access Matrix...</span>
    </div>

    <template v-else>
    <!-- ========== DESKTOP: Matrix Table ========== -->
    <div class="hidden lg:block glass-card overflow-hidden">
      <div class="overflow-x-auto relative">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-slate-50/50 dark:bg-slate-900/30 border-b border-slate-100 dark:border-slate-700">
              <th class="px-6 py-5 min-w-[220px] sticky left-0 z-20 bg-white/95 dark:bg-slate-800/95 backdrop-blur-sm shadow-r border-r border-slate-100 dark:border-slate-700">
                <div class="text-[10px] text-slate-400 font-bold uppercase tracking-widest mb-1">User / Major</div>
                <div class="text-sm font-black text-slate-800 dark:text-white">用户列表</div>
              </th>
              <th v-for="major in majors" :key="major.id" class="px-4 py-5 min-w-[100px] text-center">
                <div class="inline-block px-3 py-1.5 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 rounded-xl text-[10px] font-black uppercase tracking-tighter whitespace-nowrap leading-tight max-w-[110px] truncate" :title="major.name">
                  {{ major.name }}
                </div>
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
            <tr v-for="user in filteredUsers" :key="user.id" class="group hover:bg-slate-50/30 dark:hover:bg-slate-900/10 transition-colors">
              <td class="px-6 py-4 sticky left-0 z-10 bg-white/95 dark:bg-slate-800/95 backdrop-blur-sm border-r border-slate-100 dark:border-slate-700 group-hover:bg-indigo-50/50 dark:group-hover:bg-indigo-900/20 transition-colors">
                <div class="flex items-center gap-3">
                  <div class="w-9 h-9 rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-xs font-black text-slate-500 overflow-hidden flex-shrink-0">
                    <img v-if="user.avatar" :src="user.avatar" class="w-full h-full object-cover">
                    <span v-else>{{ (user.nickname || user.username).charAt(0).toUpperCase() }}</span>
                  </div>
                  <div class="min-w-0">
                    <div class="text-sm font-bold text-slate-800 dark:text-slate-200 truncate">{{ user.nickname || user.username }}</div>
                    <div class="text-[9px] text-slate-400 font-mono">ID: #{{ user.id }}</div>
                  </div>
                  <!-- Permission count badge -->
                  <div class="ml-auto text-[9px] font-black px-2 py-0.5 rounded-full"
                    :class="getUserPermCount(user.id) > 0 ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-700 dark:text-slate-500'"
                  >
                    {{ getUserPermCount(user.id) }}
                  </div>
                </div>
              </td>
              <td v-for="major in majors" :key="major.id" class="px-4 py-4 text-center">
                <label class="inline-flex items-center cursor-pointer group/check relative">
                  <input 
                    type="checkbox" 
                    :checked="hasPermission(user.id, major.id)"
                    @change="(e) => togglePermission(user.id, major.id, e.target.checked)"
                    class="sr-only peer"
                  >
                  <div class="w-10 h-6 bg-slate-100 dark:bg-slate-700/50 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-4 peer-checked:after:border-white after:content-[''] after:absolute after:top-[4px] after:left-[4px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-emerald-500 shadow-inner group-hover/check:scale-105 transition-transform"></div>
                </label>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ========== MOBILE/TABLET: Card Layout ========== -->
    <div class="lg:hidden space-y-3">
      <div 
        v-for="user in filteredUsers" 
        :key="user.id" 
        class="glass-card overflow-hidden transition-all duration-300"
      >
        <!-- User Header (Tap to expand) -->
        <div 
          @click="toggleExpand(user.id)" 
          class="flex items-center gap-3 p-4 cursor-pointer hover:bg-slate-50/50 dark:hover:bg-slate-700/30 transition-colors active:scale-[0.99]"
        >
          <div class="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-xs font-black text-slate-500 overflow-hidden flex-shrink-0">
            <img v-if="user.avatar" :src="user.avatar" class="w-full h-full object-cover">
            <span v-else>{{ (user.nickname || user.username).charAt(0).toUpperCase() }}</span>
          </div>
          <div class="flex-1 min-w-0">
            <div class="text-sm font-bold text-slate-800 dark:text-slate-200 truncate">{{ user.nickname || user.username }}</div>
            <div class="text-[10px] text-slate-400 font-mono">ID: #{{ user.id }}</div>
          </div>
          <!-- Permission count -->
          <div class="text-[10px] font-black px-2.5 py-1 rounded-full"
            :class="getUserPermCount(user.id) > 0 ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-slate-100 text-slate-400 dark:bg-slate-700 dark:text-slate-500'"
          >
            {{ getUserPermCount(user.id) }} / {{ majors.length }}
          </div>
          <!-- Expand Icon -->
          <div class="text-slate-400 text-xs transition-transform duration-300" :class="{ 'rotate-180': expandedUsers.has(user.id) }">
            ▼
          </div>
        </div>

        <!-- Expanded Permissions -->
        <Transition name="expand">
          <div v-if="expandedUsers.has(user.id)" class="border-t border-slate-100 dark:border-slate-700/50">
            <!-- Quick actions -->
            <div class="flex gap-2 px-4 py-3 bg-slate-50/50 dark:bg-slate-900/20 border-b border-slate-100 dark:border-slate-700/30">
              <button 
                @click="setAllPerms(user.id, true)" 
                class="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 rounded-lg text-[10px] font-black transition-all"
              >
                ✅ 全部授权
              </button>
              <button 
                @click="setAllPerms(user.id, false)" 
                class="px-3 py-1.5 bg-rose-50 hover:bg-rose-100 dark:bg-rose-500/10 text-rose-500 dark:text-rose-400 rounded-lg text-[10px] font-black transition-all"
              >
                🚫 全部撤销
              </button>
            </div>
            <!-- Major toggles -->
            <div class="divide-y divide-slate-50 dark:divide-slate-700/30">
              <div 
                v-for="major in majors" 
                :key="major.id" 
                class="flex items-center justify-between px-4 py-3 hover:bg-slate-50/30 dark:hover:bg-slate-700/20 transition-colors"
              >
                <span class="text-xs font-bold text-slate-600 dark:text-slate-300 truncate flex-1 mr-3">
                  {{ major.name }}
                </span>
                <label class="inline-flex items-center cursor-pointer group/check relative flex-shrink-0">
                  <input 
                    type="checkbox" 
                    :checked="hasPermission(user.id, major.id)"
                    @change="(e) => togglePermission(user.id, major.id, e.target.checked)"
                    class="sr-only peer"
                  >
                  <div class="w-11 h-6 bg-slate-200 dark:bg-slate-700/50 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-5 peer-checked:after:border-white after:content-[''] after:absolute after:top-[3px] after:left-[3px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-[18px] after:w-[18px] after:transition-all peer-checked:bg-emerald-500 shadow-inner transition-colors"></div>
                </label>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="filteredUsers.length === 0 && searchQuery" class="text-center py-16">
      <span class="text-3xl block mb-2 opacity-50">🔍</span>
      <p class="text-slate-400 font-bold text-sm">未找到匹配 "{{ searchQuery }}" 的用户</p>
    </div>
    </template>

    <!-- Instructions -->
    <div class="p-5 bg-indigo-50/80 dark:bg-indigo-500/5 rounded-3xl border border-indigo-100/50 dark:border-indigo-500/10">
      <h4 class="text-xs font-black text-indigo-600 dark:text-indigo-400 uppercase tracking-widest mb-2">💡 操作说明</h4>
      <ul class="text-[11px] text-indigo-500 dark:text-indigo-300/60 font-medium space-y-1.5">
        <li>• 当用户<b>没有</b>任何访问控制记录时，默认可查看<b>所有</b>专业分类。</li>
        <li>• 一旦给用户分配了<b>任意</b>专业权限，该用户将<b>仅</b>能访问被授权的分类。</li>
        <li>• 绿色开关 = 已授权，灰色开关 = 未授权。</li>
        <li>• 修改后请点击 <b>"保存更改"</b> 使配置生效。</li>
      </ul>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const users = ref([]);
const majors = ref([]);
const permissions = ref([]); // List of { user_id, major_id }
const originalPermissions = ref([]);
const loading = ref(true);
const toast = ref(null);
const confirmDialog = ref(null);
const searchQuery = ref('');
const expandedUsers = ref(new Set());

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value;
  const q = searchQuery.value.toLowerCase();
  return users.value.filter(u => 
    u.username.toLowerCase().includes(q) || 
    (u.nickname && u.nickname.toLowerCase().includes(q)) ||
    String(u.id).includes(q)
  );
});

const getUserPermCount = (userId) => {
  return permissions.value.filter(p => p.user_id === userId).length;
};

const toggleExpand = (userId) => {
  const s = new Set(expandedUsers.value);
  if (s.has(userId)) s.delete(userId);
  else s.add(userId);
  expandedUsers.value = s;
};

const setAllPerms = (userId, grant) => {
  // Remove all current perms for this user
  permissions.value = permissions.value.filter(p => p.user_id !== userId);
  if (grant) {
    // Add all majors
    majors.value.forEach(m => {
      permissions.value.push({ user_id: userId, major_id: m.id });
    });
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const [usersRes, majorsRes, permsRes] = await Promise.all([
      admin.getUsers(),
      admin.getMajors(),
      admin.getAccessControls()
    ]);
    
    users.value = usersRes.data;
    majors.value = majorsRes.data;
    
    const perms = permsRes.data.map(p => ({ user_id: p.user_id, major_id: p.major_id }));
    permissions.value = JSON.parse(JSON.stringify(perms));
    originalPermissions.value = JSON.parse(JSON.stringify(perms));
  } catch (err) {
    toast.value?.add(err.response?.data?.error || '加载数据失败', 'error');
  } finally {
    loading.value = false;
  }
};

const hasPermission = (userId, majorId) => {
  return permissions.value.some(p => p.user_id === userId && p.major_id === majorId);
};

const togglePermission = (userId, majorId, granted) => {
  if (granted) {
    permissions.value.push({ user_id: userId, major_id: majorId });
  } else {
    permissions.value = permissions.value.filter(p => !(p.user_id === userId && p.major_id === majorId));
  }
};

const hasChanges = computed(() => {
  return JSON.stringify(permissions.value.sort((a,b) => a.user_id - b.user_id || a.major_id - b.major_id)) !== 
         JSON.stringify(originalPermissions.value.sort((a,b) => a.user_id - b.user_id || a.major_id - b.major_id));
});

const saveChanges = async () => {
  const confirmed = await confirmDialog.value.confirm({
    title: '确认保存',
    message: '确认要批量更新所有用户的资源访问权限吗？',
    type: 'warning'
  });
  
  if (!confirmed) return;
  
  loading.value = true;
  try {
    const touchedUserIds = new Set([
        ...permissions.value.map(p => p.user_id),
        ...originalPermissions.value.map(p => p.user_id)
    ]);

    let updateCount = 0;
    for (const uid of touchedUserIds) {
        const currentUserPerms = permissions.value.filter(p => p.user_id === uid).map(p => p.major_id);
        const originalUserPerms = originalPermissions.value.filter(p => p.user_id === uid).map(p => p.major_id);
        
        if (JSON.stringify(currentUserPerms.sort()) === JSON.stringify(originalUserPerms.sort())) continue;

        await admin.updateAccessControlBatch({
            user_id: uid,
            major_ids: currentUserPerms
        });
        updateCount++;
    }

    toast.value?.add(`访问权限已保存（更新了 ${updateCount} 位用户）`, 'success');
    originalPermissions.value = JSON.parse(JSON.stringify(permissions.value));
  } catch (err) {
    toast.value?.add(err.response?.data?.error || '保存失败', 'error');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.glass-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(226, 232, 240, 0.5);
  border-radius: 1.5rem;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05);
}

.dark .glass-card {
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(51, 65, 85, 0.5);
}

.shadow-r {
    box-shadow: 4px 0 10px -2px rgba(0,0,0,0.05);
}

/* Expand transition */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  max-height: 1000px;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}

/* Fade transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
