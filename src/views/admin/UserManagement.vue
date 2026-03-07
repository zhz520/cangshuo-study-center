<script setup>
import { ref, reactive, onMounted } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const users = ref([]);
const loading = ref(false);
const error = ref('');
const toast = ref(null);

// Confirmation Dialog State
const confirmDialog = ref(null);

const fetchUsers = async () => {
  loading.value = true;
  try {
    const res = await admin.getUsers();
    users.value = res.data;
  } catch (err) {
    error.value = err.response?.data?.message || '获取用户列表失败';
  } finally {
    loading.value = false;
  }
};

const updateUserRole = async (user, newRole) => {
  const confirmed = await confirmDialog.value.confirm({
    title: "确认修改角色",
    message: `确认将用户 ${user.username} 的角色修改为 ${newRole} 吗？`,
    type: "warning",
    confirmText: "确认修改",
    cancelText: "取消"
  });
  if (!confirmed) return;
  try {
    await admin.updateUserRole(user.id, newRole);
    user.role = newRole;
    toast.value?.add('修改成功', 'success');
  } catch (err) {
    toast.value?.add(err.response?.data?.message || '修改失败', 'error');
  }
};

const deleteUser = async (id) => {
  const confirmed = await confirmDialog.value.confirm({
    title: "确认删除用户", 
    message: '确认删除该用户吗？此操作不可恢复！', 
    type: "danger",
    confirmText: "确认删除",
    cancelText: "取消"
  });
  if (!confirmed) return;
  try {
    await admin.deleteUser(id);
    users.value = users.value.filter(u => u.id !== id);
    toast.value?.add('删除成功', 'success');
    fetchUsers(); // Refresh list to be sure
  } catch (err) {
    toast.value?.add(err.response?.data?.message || '删除失败', 'error');
  }
};

// --- Add / Edit User ---
const showUserModal = ref(false);
const editingUser = ref(null);
const userForm = ref({ username: '', password: '', role: 'user' });

const openAddUser = () => {
    editingUser.value = null;
    userForm.value = { username: '', password: '', role: 'user' };
    showUserModal.value = true;
};

const openEditUser = (user) => {
    editingUser.value = user;
    // Don't fill password
    userForm.value = { username: user.username, password: '', role: user.role };
    showUserModal.value = true;
};

const handleSaveUser = async () => {
  try {
    if (editingUser.value) {
        // Update
        const updates = {};
        if (userForm.value.username !== editingUser.value.username) updates.username = userForm.value.username;
        if (userForm.value.password) updates.password = userForm.value.password;
        if (userForm.value.role !== editingUser.value.role) updates.role = userForm.value.role;
        
        await admin.updateUserDetails(editingUser.value.id, updates);
        toast.value?.add('用户更新成功', 'success');
    } else {
        // Create
        if (!userForm.value.username || !userForm.value.password) {
            toast.value?.add('用户名和密码不能为空', 'warning');
            return;
        }
        await admin.createUser(userForm.value);
        toast.value?.add('用户创建成功', 'success');
    }
    showUserModal.value = false;
    fetchUsers();
  } catch (err) {
    toast.value?.add(err.response?.data?.message || '保存失败', 'error');
  }
};

onMounted(() => {
    fetchUsers();
});
</script>

<template>
  <div class="p-6 space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
            <h2 class="text-2xl font-black text-slate-800 dark:text-white tracking-tight">用户管理</h2>
            <p class="text-sm text-slate-500 dark:text-slate-400 font-medium">管理与维护系统用户信息</p>
        </div>
        <button @click="openAddUser" class="group relative px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-2xl shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95 overflow-hidden">
            <div class="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>
            <span class="relative font-bold flex items-center gap-2 text-sm">
                <span>+</span> 新增用户
            </span>
        </button>
    </div>

    <!-- User Modal -->
    <div v-if="showUserModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md transition-all animate-fade-in p-4">
        <div class="glass-card w-full max-w-md p-8 relative overflow-hidden shadow-2xl animate-scale-up">
            <!-- Decorative Background -->
            <div class="absolute top-0 right-0 w-32 h-32 bg-indigo-500/10 rounded-full blur-[40px] -translate-y-1/2 translate-x-1/2"></div>
            
            <h3 class="text-xl font-black text-slate-800 dark:text-white mb-6 relative z-10">
                {{ editingUser ? '编辑用户' : '新增用户' }}
            </h3>
            
            <div class="space-y-5 relative z-10">
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">用户名</label>
                    <div class="relative group">
                        <input v-model="userForm.username" type="text" class="w-full pl-4 pr-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all group-hover:border-indigo-300 dark:group-hover:border-indigo-700">
                    </div>
                </div>
                
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">
                        密码 {{ editingUser ? '(留空则不修改)' : '' }}
                    </label>
                    <div class="relative group">
                        <input v-model="userForm.password" type="text" class="w-full pl-4 pr-4 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all group-hover:border-indigo-300 dark:group-hover:border-indigo-700">
                    </div>
                </div>
                
                <div class="space-y-1.5">
                    <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">角色权限</label>
                    <div class="relative">
                        <select v-model="userForm.role" class="w-full pl-4 pr-10 py-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 appearance-none cursor-pointer">
                            <option value="user">普通用户</option>
                            <option value="demo">展示用户</option>
                            <option value="admin">管理员</option>
                        </select>
                        <div class="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none text-xs">▼</div>
                    </div>
                </div>
            </div>

            <div class="flex gap-3 mt-8 relative z-10">
                <button @click="showUserModal = false" class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-black transition-colors">取消</button>
                <button @click="handleSaveUser" class="flex-1 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-black shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95">保存更改</button>
            </div>
        </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="glass-card p-12 flex flex-col items-center justify-center text-slate-400 animate-pulse">
        <div class="w-12 h-12 rounded-full border-4 border-slate-200 border-t-indigo-500 animate-spin mb-4"></div>
        <span class="font-bold text-sm tracking-wider">LOADING DATA...</span>
    </div>
    
    <!-- Error State -->
    <div v-else-if="error" class="glass-card p-10 text-center">
        <div class="text-4xl mb-4">⚠️</div>
        <div class="text-rose-500 font-bold mb-2">数据加载失败</div>
        <div class="text-slate-400 text-sm">{{ error }}</div>
        <button @click="fetchUsers" class="mt-4 px-4 py-2 bg-slate-100 dark:bg-slate-800 rounded-lg text-xs font-bold text-slate-600 dark:text-slate-400 hover:text-indigo-500 transition-colors">重试</button>
    </div>

    <template v-else>
        <!-- Desktop Table View -->
        <div class="hidden lg:block glass-card overflow-hidden">
          <table class="w-full text-left border-collapse">
            <thead>
              <tr class="border-b border-slate-200/60 dark:border-slate-700/60 text-xs text-slate-500 dark:text-slate-400 uppercase tracking-wider font-bold bg-slate-50/50 dark:bg-slate-800/30">
                <th class="px-8 py-5">UID</th>
                <th class="px-8 py-5">用户凭证</th>
                <th class="px-8 py-5">权限等级</th>
                <th class="px-8 py-5">注册时间</th>
                <th class="px-8 py-5 text-right">管理操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 dark:divide-slate-700/50">
              <tr v-for="user in users" :key="user.id" class="group hover:bg-indigo-50/30 dark:hover:bg-indigo-900/10 transition-colors duration-200">
                <td class="px-8 py-5">
                    <span class="font-mono text-xs font-bold text-slate-400">#{{ user.id }}</span>
                </td>
                <td class="px-8 py-5">
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-100 to-purple-100 dark:from-indigo-900 dark:to-purple-900 flex items-center justify-center text-xs font-bold text-indigo-600 dark:text-indigo-300">
                            {{ user.username.charAt(0).toUpperCase() }}
                        </div>
                        <div class="font-bold text-slate-700 dark:text-slate-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                            {{ user.username }}
                        </div>
                    </div>
                </td>
                <td class="px-8 py-5">
                  <span class="inline-flex items-center px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wider shadow-sm" 
                    :class="{
                        'bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300': user.role === 'admin',
                        'bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-300': user.role === 'demo',
                        'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400': user.role !== 'admin' && user.role !== 'demo'
                    }">
                    <span class="w-1.5 h-1.5 rounded-full mr-1.5" :class="{
                        'bg-indigo-500': user.role === 'admin',
                        'bg-orange-500': user.role === 'demo',
                        'bg-slate-400': user.role !== 'admin' && user.role !== 'demo'
                    }"></span>
                    {{ user.role === 'demo' ? '展示用户' : (user.role === 'admin' ? '管理员' : '普通用户') }}
                  </span>
                </td>
                <td class="px-8 py-5">
                    <div class="text-xs font-medium text-slate-500 dark:text-slate-400">
                        {{ new Date(user.created_at).toLocaleDateString() }}
                        <span class="text-slate-300 dark:text-slate-600 mx-1">|</span>
                        {{ new Date(user.created_at).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) }}
                    </div>
                </td>
                <td class="px-8 py-5 text-right">
                    <div class="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                         <button 
                          @click="openEditUser(user)" 
                          class="p-2 rounded-lg text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-900/30 transition-colors"
                          title="编辑信息"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                        </button>
                        <button 
                          @click="deleteUser(user.id)"
                          class="p-2 rounded-lg text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 transition-colors"
                           title="删除用户"
                        >
                           <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
                        </button>
                    </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile Card View -->
        <div class="lg:hidden grid gap-4">
          <div v-for="user in users" :key="user.id" class="glass-card p-5 relative overflow-hidden active:scale-[0.99] transition-transform">
            <div class="flex justify-between items-start mb-4">
              <div class="flex items-center gap-3">
                 <div class="w-10 h-10 rounded-full bg-gradient-to-br from-indigo-100 to-purple-100 dark:from-indigo-900 dark:to-purple-900 flex items-center justify-center text-sm font-black text-indigo-600 dark:text-indigo-300">
                     {{ user.username.charAt(0).toUpperCase() }}
                </div>
                <div>
                  <div class="font-black text-slate-800 dark:text-white">{{ user.username }}</div>
                  <div class="text-[10px] text-slate-400 font-bold font-mono">UID: #{{ user.id }}</div>
                </div>
              </div>
              <span class="px-2.5 py-1 rounded-full text-[9px] font-black uppercase tracking-wider" 
                :class="{
                    'bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300': user.role === 'admin',
                    'bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-300': user.role === 'demo',
                    'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400': user.role !== 'admin' && user.role !== 'demo'
                }">
                {{ user.role === 'demo' ? '展示' : (user.role === 'admin' ? '管理员' : '用户') }}
              </span>
            </div>
            
            <div class="bg-slate-50/50 dark:bg-slate-800/50 rounded-xl p-3 mb-4 flex justify-between items-center text-xs">
              <span class="text-slate-400 font-bold">注册时间</span>
              <span class="text-slate-600 dark:text-slate-300 font-mono">{{ new Date(user.created_at).toLocaleDateString() }}</span>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <button @click="openEditUser(user)" class="py-2.5 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-xl text-xs font-black transition-colors hover:bg-indigo-100 dark:hover:bg-indigo-900/40">
                  编辑详情
              </button>
              <button @click="deleteUser(user.id)" class="py-2.5 bg-rose-50 dark:bg-rose-900/20 text-rose-500 dark:text-rose-400 rounded-xl text-xs font-black transition-colors hover:bg-rose-100 dark:hover:bg-rose-900/40">
                  注销账号
              </button>
            </div>
          </div>
        </div>
    </template>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

